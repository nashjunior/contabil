package br.contabil.prestacaocontas.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.entrega.ServicoEntrega;
import br.contabil.prestacaocontas.domain.RemessaSimTceCe;

class PublicacaoRemessaSimTceCeOutboxAdapterTest {

    @Test
    @DisplayName("publica remessa SIM via ServicoEntrega com destino SIMWEB e chave idempotente")
    void publicaViaOutboxIdempotente() {
        TenantId enteId = new TenantId(UUID.randomUUID());
        ServicoEntregaFake entrega = new ServicoEntregaFake();
        RemessaSimTceCe remessa = new RemessaSimTceCe(
                enteId,
                2026,
                3,
                "308",
                "BA202603.BAL",
                "308,2026".getBytes(StandardCharsets.US_ASCII),
                "BA202603.zip",
                new byte[] {1, 2, 3});
        ChaveIdempotencia chave = ChaveIdempotencia.de("sim-tce-ce:308:%s:2026:03".formatted(enteId.valor()));

        ServicoEntrega.IdEntrega id = new PublicacaoRemessaSimTceCeOutboxAdapter(entrega).publicar(remessa, chave);

        assertThat(id).isEqualTo(entrega.id);
        assertThat(entrega.chave).isEqualTo(chave);
        assertThat(entrega.mensagem.destino()).isEqualTo("tce-ce-simweb");
        assertThat(entrega.mensagem.tipo()).isEqualTo("prestacao_contas.sim_tce_ce.remessa.v1");
        assertThat(entrega.mensagem.conteudo())
                .contains("\"tabela\":\"308\"")
                .contains("\"prazo\":\"2026-04-30\"")
                .contains("\"nomeArquivoZip\":\"BA202603.zip\"")
                .contains("\"conteudoZipBase64\":\"AQID\"");
    }

    private static final class ServicoEntregaFake implements ServicoEntrega {
        private final IdEntrega id = new IdEntrega(UUID.randomUUID());
        private MensagemEntrega mensagem;
        private ChaveIdempotencia chave;

        @Override
        public IdEntrega enqueue(MensagemEntrega mensagem, ChaveIdempotencia chave) {
            this.mensagem = mensagem;
            this.chave = chave;
            return id;
        }

        @Override
        public StatusEntrega status(IdEntrega id) {
            return StatusEntrega.ENFILEIRADO;
        }
    }
}
