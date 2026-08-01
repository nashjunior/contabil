package br.contabil.assinatura;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.DocumentoAssinadoEmpenho;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ReferenciaFatoContabil;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

class AssinaturaEmpenhoResponseTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void respostaNaoExpoeUriInternaDoObjectStore() throws Exception {
        UUID enteId = UUID.randomUUID();
        EmpenhoId empenhoId = EmpenhoId.novo();
        UUID idTransacao = UUID.randomUUID();
        URI pendente = URI.create("s3://ged/%s/execucao/empenho/%s/nota-empenho.pdf"
                .formatted(enteId, empenhoId.valor()));
        URI assinado = URI.create("s3://ged/%s/execucao/empenho/%s/nota-empenho-assinado.pdf"
                .formatted(enteId, empenhoId.valor()));
        Empenho empenho = empenhoRegistrado(enteId, empenhoId)
                .marcarPendenteAssinatura(pendente)
                .assinar(new DocumentoAssinadoEmpenho(
                        assinado,
                        "hash-abc",
                        "manifesto",
                        idTransacao,
                        NivelAssinatura.AVANCADA_GOVBR,
                        new Cpf("98765432109"),
                        Instant.parse("2026-08-01T12:00:00Z")));

        AssinaturaEmpenhoResponse resposta = AssinaturaEmpenhoResponse.de(empenho);

        assertThat(resposta.documentoAssinado()).isTrue();
        assertThat(resposta.hashSha256()).isEqualTo("hash-abc");
        assertThat(resposta.idTransacao()).isEqualTo(idTransacao);

        String corpo = json.writeValueAsString(resposta);
        assertThat(corpo)
                .doesNotContain("s3://")
                .doesNotContain("ged")
                .doesNotContain("nota-empenho-assinado.pdf")
                .doesNotContain("documentoAssinadoUri")
                .contains("\"documentoAssinado\":true");
    }

    private Empenho empenhoRegistrado(UUID enteId, EmpenhoId empenhoId) {
        return Empenho.registrar(
                empenhoId,
                new TenantId(enteId),
                9L,
                2026,
                TipoEmpenho.ORDINARIO,
                DotacaoId.novo(),
                CredorId.novo(),
                UnidadeGestoraId.novo(),
                null,
                Dinheiro.de("5000.00"),
                LocalDate.of(2026, 7, 20),
                "04.122.0001.2001",
                "0100000000",
                "empenho de teste RAZ-179",
                new ReferenciaFatoContabil(UUID.randomUUID()),
                new Cpf("11122233344"));
    }
}
