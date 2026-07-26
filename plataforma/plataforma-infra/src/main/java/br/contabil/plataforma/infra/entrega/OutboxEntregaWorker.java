package br.contabil.plataforma.infra.entrega;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OutboxEntregaWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxEntregaWorker.class);

    private final OutboxMensagemRepository repository;
    private final BrokerEntrega broker;
    private final OutboxEntregaProperties properties;
    private final Clock clock;

    public OutboxEntregaWorker(
            OutboxMensagemRepository repository,
            BrokerEntrega broker,
            OutboxEntregaProperties properties,
            Clock clock) {
        this.repository = repository;
        this.broker = broker;
        this.properties = properties;
        this.clock = clock;
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
        try {
            broker.publicar(mensagem);
            repository.confirmarEntrega(mensagem.id());
        } catch (FalhaPermanenteEntregaException ex) {
            repository.enviarParaDlq(mensagem.id(), mensagemErro(ex));
            LOGGER.warn(
                    "outbox_entrega_dlq id={} destino={} tipo={} motivo=falha_permanente",
                    mensagem.id().valor(),
                    mensagem.destino(),
                    mensagem.tipo());
        } catch (RuntimeException ex) {
            registrarFalhaTransiente(mensagem, ex);
        }
        return 1;
    }

    private void registrarFalhaTransiente(MensagemOutbox mensagem, RuntimeException ex) {
        int tentativaAtual = mensagem.tentativas() + 1;
        String erro = mensagemErro(ex);
        if (tentativaAtual >= properties.maxTentativasEfetivo()) {
            repository.enviarParaDlq(mensagem.id(), erro);
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

    private static String mensagemErro(RuntimeException ex) {
        String mensagem = ex.getMessage();
        return mensagem == null || mensagem.isBlank() ? ex.getClass().getName() : mensagem;
    }
}
