package br.contabil.plataforma.infra.entrega;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.contabil.plataforma.infra.observabilidade.CorrelacaoIds;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class OutboxEntregaWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxEntregaWorker.class);
    private static final String DESTINO_TRANSPARENCIA = "transparencia";
    private static final String METRICA_PROCESSADAS = "siafic.publicacao.transparencia.processadas";
    private static final String METRICA_LATENCIA = "siafic.publicacao.transparencia.latencia";

    private final OutboxMensagemRepository repository;
    private final BrokerEntrega broker;
    private final OutboxEntregaProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public OutboxEntregaWorker(
            OutboxMensagemRepository repository,
            BrokerEntrega broker,
            OutboxEntregaProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.broker = broker;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public int processarPendentes() {
        Instant agora = clock.instant();
        return repository
                .reclamar(
                        properties.batchSizeEfetivo(),
                        agora.plus(properties.lockDurationEfetivo()))
                .stream()
                .mapToInt(this::processarUma)
                .sum();
    }

    private int processarUma(MensagemOutbox mensagem) {
        try (CorrelacaoIds.Escopo ignored = CorrelacaoIds.escopo(mensagem.correlationId())) {
            try {
                broker.publicar(mensagem);
                repository.confirmarEntrega(mensagem.id());
                registrarMetricaTransparencia(mensagem, "entregue");
            } catch (FalhaPermanenteEntregaException ex) {
                repository.enviarParaDlq(mensagem.id(), mensagemErro(ex));
                registrarMetricaTransparencia(mensagem, "dlq");
                LOGGER.warn(
                        "outbox_entrega_dlq id={} destino={} tipo={} motivo=falha_permanente",
                        mensagem.id().valor(),
                        mensagem.destino(),
                        mensagem.tipo());
            } catch (RuntimeException ex) {
                registrarFalhaTransiente(mensagem, ex);
            }
        }
        return 1;
    }

    private void registrarFalhaTransiente(MensagemOutbox mensagem, RuntimeException ex) {
        int tentativaAtual = mensagem.tentativas() + 1;
        String erro = mensagemErro(ex);
        if (tentativaAtual >= properties.maxTentativasEfetivo()) {
            repository.enviarParaDlq(mensagem.id(), erro);
            registrarMetricaTransparencia(mensagem, "dlq");
            LOGGER.warn(
                    "outbox_entrega_dlq id={} destino={} tipo={} tentativas={}",
                    mensagem.id().valor(),
                    mensagem.destino(),
                    mensagem.tipo(),
                    tentativaAtual);
            return;
        }

        repository.registrarRetentativa(
                mensagem.id(),
                clock.instant().plus(backoff(tentativaAtual)),
                erro);
        registrarMetricaTransparencia(mensagem, "retentativa");
        LOGGER.info(
                "outbox_entrega_retentativa id={} destino={} tipo={} tentativa={}",
                mensagem.id().valor(),
                mensagem.destino(),
                mensagem.tipo(),
                tentativaAtual);
    }

    private Duration backoff(int tentativaAtual) {
        Duration base = properties.backoffBaseEfetivo();
        Duration max = properties.backoffMaxEfetivo();
        long multiplicador = 1L << Math.min(tentativaAtual - 1, 10);
        Duration calculado = base.multipliedBy(multiplicador);
        return calculado.compareTo(max) > 0 ? max : calculado;
    }

    private void registrarMetricaTransparencia(MensagemOutbox mensagem, String resultado) {
        if (!DESTINO_TRANSPARENCIA.equals(mensagem.destino())) {
            return;
        }
        Counter.builder(METRICA_PROCESSADAS)
                .description("Mensagens de publicação na transparência processadas pelo outbox")
                .tag("resultado", resultado)
                .register(meterRegistry)
                .increment();
        Timer.builder(METRICA_LATENCIA)
                .description("Latência entre enfileiramento e processamento da publicação na transparência")
                .tag("resultado", resultado)
                .register(meterRegistry)
                .record(Duration.between(mensagem.criadoEm(), clock.instant()));
    }

    private static String mensagemErro(RuntimeException ex) {
        String mensagem = ex.getMessage();
        return mensagem == null || mensagem.isBlank() ? ex.getClass().getName() : mensagem;
    }
}
