package br.contabil.execucao.infra.documento;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.contabil.execucao.application.GerarDocumentoEmpenho;
import br.contabil.plataforma.infra.observabilidade.CorrelacaoIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Consumidor do outbox dedicado de {@code execucao_empenho_pendente_documento}
 * (ADR-0027 (b)) — mesma forma de {@code OutboxEntregaWorker}: reclama um lote
 * fora da transação do fato, processa cada mensagem e confirma/retenta/DLQ.
 *
 * <p>Diferente de {@code OutboxEntregaWorker} (que despacha a um {@code
 * BrokerEntrega} externo), o "processamento" aqui É o caso de uso {@link
 * GerarDocumentoEmpenho}: roda dentro do próprio {@code executar(TenantId, ..)}
 * advisado (transação + {@code app.ente_id} setados automaticamente pelos
 * advisors da borda, RAZ-14/RAZ-21), mesmo chamado a partir deste worker de
 * infra. Registrado como {@code @Bean} por {@link EmpenhoDocumentoPendenteConfiguration}
 * (não {@code @Component}): só existe quando o object store está configurado
 * (mesmo pré-requisito de {@link GerarDocumentoEmpenho}).
 */
public class EmpenhoDocumentoPendenteWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmpenhoDocumentoPendenteWorker.class);

    private final EmpenhoDocumentoOutboxRepository repository;
    private final GerarDocumentoEmpenho gerarDocumentoEmpenho;
    private final EmpenhoDocumentoPendenteProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public EmpenhoDocumentoPendenteWorker(
            EmpenhoDocumentoOutboxRepository repository,
            GerarDocumentoEmpenho gerarDocumentoEmpenho,
            EmpenhoDocumentoPendenteProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.gerarDocumentoEmpenho = gerarDocumentoEmpenho;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public int processarPendentes() {
        Instant agora = clock.instant();
        return repository
                .reclamar(properties.batchSizeEfetivo(), agora.plus(properties.lockDurationEfetivo()))
                .stream()
                .mapToInt(this::processarUma)
                .sum();
    }

    private int processarUma(MensagemEmpenhoDocumentoOutbox mensagem) {
        try (CorrelacaoIds.Escopo ignored = CorrelacaoIds.escopo(mensagem.correlationId())) {
            try {
                gerarDocumentoEmpenho.executar(mensagem.enteId(), mensagem.empenhoId());
                repository.confirmar(mensagem.id());
                registrarMetrica("concluido");
            } catch (RuntimeException ex) {
                registrarFalha(mensagem, ex);
            }
        }
        return 1;
    }

    private void registrarFalha(MensagemEmpenhoDocumentoOutbox mensagem, RuntimeException ex) {
        int tentativaAtual = mensagem.tentativas() + 1;
        String erro = mensagemErro(ex);
        if (tentativaAtual >= properties.maxTentativasEfetivo()) {
            repository.enviarParaDlq(mensagem.id(), erro);
            registrarMetrica("dlq");
            LOGGER.warn(
                    "execucao_empenho_documento_outbox_dlq id={} empenhoId={} tentativas={}",
                    mensagem.id(),
                    mensagem.empenhoId().valor(),
                    tentativaAtual);
            return;
        }

        repository.registrarRetentativa(mensagem.id(), clock.instant().plus(backoff(tentativaAtual)), erro);
        registrarMetrica("retentativa");
        LOGGER.info(
                "execucao_empenho_documento_outbox_retentativa id={} empenhoId={} tentativa={}",
                mensagem.id(),
                mensagem.empenhoId().valor(),
                tentativaAtual);
    }

    private Duration backoff(int tentativaAtual) {
        Duration base = properties.backoffBaseEfetivo();
        Duration max = properties.backoffMaxEfetivo();
        long multiplicador = 1L << Math.min(tentativaAtual - 1, 10);
        Duration calculado = base.multipliedBy(multiplicador);
        return calculado.compareTo(max) > 0 ? max : calculado;
    }

    private void registrarMetrica(String resultado) {
        Counter.builder("siafic.execucao.empenho_documento.outbox.processadas")
                .description("Mensagens do outbox de documento de empenho processadas")
                .tag("resultado", resultado)
                .register(meterRegistry)
                .increment();
    }

    private static String mensagemErro(RuntimeException ex) {
        String mensagem = ex.getMessage();
        return mensagem == null || mensagem.isBlank() ? ex.getClass().getName() : mensagem;
    }
}
