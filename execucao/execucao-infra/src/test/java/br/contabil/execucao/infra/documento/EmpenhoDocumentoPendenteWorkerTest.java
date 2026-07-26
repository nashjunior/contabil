package br.contabil.execucao.infra.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.contabil.execucao.application.GerarDocumentoEmpenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.plataforma.domain.TenantId;

@ExtendWith(MockitoExtension.class)
class EmpenhoDocumentoPendenteWorkerTest {

    private static final Instant AGORA = Instant.parse("2026-07-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Mock
    private GerarDocumentoEmpenho gerarDocumentoEmpenho;

    private FakeEmpenhoDocumentoOutboxRepository repository;
    private EmpenhoDocumentoPendenteProperties properties;
    private EmpenhoDocumentoPendenteWorker worker;

    @BeforeEach
    void setUp() {
        repository = new FakeEmpenhoDocumentoOutboxRepository();
        properties = new EmpenhoDocumentoPendenteProperties();
        properties.setBatchSize(10);
        properties.setMaxTentativas(3);
        properties.setLockDuration(Duration.ofMinutes(2));
        properties.setBackoffBase(Duration.ofSeconds(5));
        properties.setBackoffMax(Duration.ofMinutes(1));
        worker = new EmpenhoDocumentoPendenteWorker(repository, gerarDocumentoEmpenho, properties, CLOCK);
    }

    @Test
    void processaMensagemReclamadaEConfirma() {
        MensagemEmpenhoDocumentoOutbox mensagem = mensagem(0);
        repository.reclamadas.add(mensagem);
        doNothing().when(gerarDocumentoEmpenho).executar(mensagem.enteId(), mensagem.empenhoId());

        int processadas = worker.processarPendentes();

        assertThat(processadas).isEqualTo(1);
        assertThat(repository.limiteRecebido).isEqualTo(10);
        assertThat(repository.bloqueadoAteRecebido).isEqualTo(AGORA.plus(Duration.ofMinutes(2)));
        assertThat(repository.confirmadas).containsExactly(mensagem.id());
        assertThat(repository.retentativas).isEmpty();
        assertThat(repository.dlq).isEmpty();
    }

    @Test
    void falhaTransienteAgendaRetentativaComBackoffExponencial() {
        MensagemEmpenhoDocumentoOutbox mensagem = mensagem(1);
        repository.reclamadas.add(mensagem);
        doThrow(new IllegalStateException("object store indisponível"))
                .when(gerarDocumentoEmpenho).executar(mensagem.enteId(), mensagem.empenhoId());

        int processadas = worker.processarPendentes();

        assertThat(processadas).isEqualTo(1);
        assertThat(repository.confirmadas).isEmpty();
        assertThat(repository.dlq).isEmpty();
        assertThat(repository.retentativas).containsExactly(
                new Retentativa(mensagem.id(), AGORA.plus(Duration.ofSeconds(10)), "object store indisponível"));
    }

    @Test
    void falhaEsgotaTentativasEVaiParaDlq() {
        MensagemEmpenhoDocumentoOutbox mensagem = mensagem(2);
        repository.reclamadas.add(mensagem);
        doThrow(new IllegalStateException("falha ao gerar pdf"))
                .when(gerarDocumentoEmpenho).executar(mensagem.enteId(), mensagem.empenhoId());

        worker.processarPendentes();

        assertThat(repository.confirmadas).isEmpty();
        assertThat(repository.retentativas).isEmpty();
        assertThat(repository.dlq).containsExactly(new Dlq(mensagem.id(), "falha ao gerar pdf"));
    }

    @Test
    void loteVazioNaoInterageComOCasoDeUso() {
        int processadas = worker.processarPendentes();

        assertThat(processadas).isZero();
        verifyNoInteractions(gerarDocumentoEmpenho);
    }

    private static MensagemEmpenhoDocumentoOutbox mensagem(int tentativas) {
        return new MensagemEmpenhoDocumentoOutbox(
                UUID.randomUUID(), new TenantId(UUID.randomUUID()), EmpenhoId.novo(), tentativas);
    }

    private static final class FakeEmpenhoDocumentoOutboxRepository implements EmpenhoDocumentoOutboxRepository {

        private final List<MensagemEmpenhoDocumentoOutbox> reclamadas = new ArrayList<>();
        private final List<UUID> confirmadas = new ArrayList<>();
        private final List<Retentativa> retentativas = new ArrayList<>();
        private final List<Dlq> dlq = new ArrayList<>();
        private int limiteRecebido;
        private Instant bloqueadoAteRecebido;

        @Override
        public List<MensagemEmpenhoDocumentoOutbox> reclamar(int limite, Instant bloqueadoAte) {
            this.limiteRecebido = limite;
            this.bloqueadoAteRecebido = bloqueadoAte;
            return reclamadas;
        }

        @Override
        public void confirmar(UUID id) {
            confirmadas.add(id);
        }

        @Override
        public void registrarRetentativa(UUID id, Instant proximaTentativa, String erro) {
            retentativas.add(new Retentativa(id, proximaTentativa, erro));
        }

        @Override
        public void enviarParaDlq(UUID id, String erro) {
            dlq.add(new Dlq(id, erro));
        }
    }

    private record Retentativa(UUID id, Instant proximaTentativa, String erro) {}

    private record Dlq(UUID id, String erro) {}
}
