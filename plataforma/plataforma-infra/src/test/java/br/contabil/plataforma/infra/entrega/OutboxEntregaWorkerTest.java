package br.contabil.plataforma.infra.entrega;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;

class OutboxEntregaWorkerTest {

    private static final Instant AGORA = Instant.parse("2026-07-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

    private FakeOutboxRepository repository;
    private OutboxEntregaProperties properties;

    @BeforeEach
    void setUp() {
        repository = new FakeOutboxRepository();
        properties = new OutboxEntregaProperties();
        properties.setBatchSize(10);
        properties.setMaxTentativas(3);
        properties.setLockDuration(Duration.ofMinutes(2));
        properties.setBackoffBase(Duration.ofSeconds(5));
        properties.setBackoffMax(Duration.ofMinutes(1));
    }

    @Test
    void publicaMensagemReclamadaEConfirmaEntrega() {
        MensagemOutbox mensagem = mensagem(0);
        repository.reclamadas.add(mensagem);
        List<MensagemOutbox> publicadas = new ArrayList<>();
        OutboxEntregaWorker worker = worker(publicadas::add);

        int processadas = worker.processarPendentes();

        assertThat(processadas).isEqualTo(1);
        assertThat(repository.limiteRecebido).isEqualTo(10);
        assertThat(repository.bloqueadoAteRecebido).isEqualTo(AGORA.plus(Duration.ofMinutes(2)));
        assertThat(publicadas).containsExactly(mensagem);
        assertThat(repository.confirmadas).containsExactly(mensagem.id());
        assertThat(repository.retentativas).isEmpty();
        assertThat(repository.dlq).isEmpty();
    }

    @Test
    void falhaTransienteAgendaRetentativaComBackoffExponencial() {
        MensagemOutbox mensagem = mensagem(1);
        repository.reclamadas.add(mensagem);
        OutboxEntregaWorker worker = worker(msg -> {
            throw new IllegalStateException("broker indisponível");
        });

        int processadas = worker.processarPendentes();

        assertThat(processadas).isEqualTo(1);
        assertThat(repository.confirmadas).isEmpty();
        assertThat(repository.dlq).isEmpty();
        assertThat(repository.retentativas).containsExactly(new Retentativa(
                mensagem.id(),
                AGORA.plus(Duration.ofSeconds(10)),
                "broker indisponível"));
    }

    @Test
    void falhaTransienteVaiParaDlqAoEsgotarTentativas() {
        MensagemOutbox mensagem = mensagem(2);
        repository.reclamadas.add(mensagem);
        OutboxEntregaWorker worker = worker(msg -> {
            throw new IllegalStateException("timeout");
        });

        worker.processarPendentes();

        assertThat(repository.confirmadas).isEmpty();
        assertThat(repository.retentativas).isEmpty();
        assertThat(repository.dlq).containsExactly(new Dlq(mensagem.id(), "timeout"));
    }

    @Test
    void falhaPermanenteVaiDiretoParaDlq() {
        MensagemOutbox mensagem = mensagem(0);
        repository.reclamadas.add(mensagem);
        OutboxEntregaWorker worker = worker(msg -> {
            throw new FalhaPermanenteEntregaException("payload inválido");
        });

        worker.processarPendentes();

        assertThat(repository.confirmadas).isEmpty();
        assertThat(repository.retentativas).isEmpty();
        assertThat(repository.dlq).containsExactly(new Dlq(mensagem.id(), "payload inválido"));
    }

    private OutboxEntregaWorker worker(BrokerEntrega broker) {
        return new OutboxEntregaWorker(repository, broker, properties, CLOCK);
    }

    private static MensagemOutbox mensagem(int tentativas) {
        return new MensagemOutbox(
                new IdEntrega(UUID.randomUUID()),
                new TenantId(UUID.randomUUID()),
                ChaveIdempotencia.de("transparencia:execucao:" + UUID.randomUUID()),
                "transparencia",
                "execucao.empenho.criado",
                "{\"id\":\"1\"}",
                tentativas);
    }

    private static final class FakeOutboxRepository implements OutboxMensagemRepository {

        private final List<MensagemOutbox> reclamadas = new ArrayList<>();
        private final List<IdEntrega> confirmadas = new ArrayList<>();
        private final List<Retentativa> retentativas = new ArrayList<>();
        private final List<Dlq> dlq = new ArrayList<>();
        private int limiteRecebido;
        private Instant bloqueadoAteRecebido;

        @Override
        public List<MensagemOutbox> reclamar(int limite, Instant bloqueadoAte) {
            this.limiteRecebido = limite;
            this.bloqueadoAteRecebido = bloqueadoAte;
            return reclamadas;
        }

        @Override
        public void confirmarEntrega(IdEntrega id) {
            confirmadas.add(id);
        }

        @Override
        public void registrarRetentativa(IdEntrega id, Instant proximaTentativa, String erro) {
            retentativas.add(new Retentativa(id, proximaTentativa, erro));
        }

        @Override
        public void enviarParaDlq(IdEntrega id, String erro) {
            dlq.add(new Dlq(id, erro));
        }
    }

    private record Retentativa(IdEntrega id, Instant proximaTentativa, String erro) {}

    private record Dlq(IdEntrega id, String erro) {}
}
