package br.contabil.transparencia;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.contabil.plataforma.application.ConsultarTransparenciaPublica;
import br.contabil.plataforma.application.TotalizarTransparenciaPublica;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.transparencia.FiltroTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.PaginaTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.PublicacaoTransparencia;
import br.contabil.plataforma.domain.transparencia.TotalizacaoTransparenciaPublica;

@ExtendWith(MockitoExtension.class)
class TransparenciaPublicaControllerTest {

    @Mock
    private ConsultarTransparenciaPublica consultar;

    @Mock
    private TotalizarTransparenciaPublica totalizar;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void jsonCsvEBulkNaoVazamCpfRgEnderecoOuContaBancariaEPreservamCnpjERemuneracaoNominal() throws Exception {
        UUID enteId = UUID.randomUUID();
        FiltroTransparenciaPublica filtroEsperado = new FiltroTransparenciaPublica(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), "publicadoEm",
                FiltroTransparenciaPublica.DirecaoOrdenacao.DESC, 50, Optional.empty());
        when(consultar.executar(eq(new TenantId(enteId)), eq(filtroEsperado)))
                .thenReturn(new PaginaTransparenciaPublica(
                        List.of(publicacao(enteId)),
                        Optional.empty(),
                        false,
                        1,
                        Optional.of(Instant.parse("2026-08-02T12:00:00Z"))));
        when(totalizar.executar(eq(new TenantId(enteId)), eq(filtroEsperado)))
                .thenReturn(new TotalizacaoTransparenciaPublica(
                        List.of(new TotalizacaoTransparenciaPublica.Linha("pago", new BigDecimal("1200.00"), 1)),
                        new BigDecimal("1200.00"),
                        1,
                        Optional.of(Instant.parse("2026-08-02T12:00:00Z"))));

        TransparenciaPublicaController controller = new TransparenciaPublicaController(consultar, totalizar, mapper);

        String json = mapper.writeValueAsString(controller.despesas(
                enteId, null, null, null, null, null, null, null, null,
                "publicadoEm", "desc", null, null, null, null).getBody());
        String csv = (String) controller.despesas(
                enteId, null, null, null, null, null, null, null, null,
                "publicadoEm", "desc", null, null, "csv", null).getBody();
        String bulk = mapper.writeValueAsString(controller.bulk(
                enteId, null, null, null, null, null, null, null, null).getBody());

        assertPublicoSemPiiIntegra(json);
        assertPublicoSemPiiIntegra(csv);
        assertPublicoSemPiiIntegra(bulk);
        assertThat(json).contains("12345678000199", "9876.54", "***.456.***-**");
        assertThat(csv).contains("***.456.***-**");
        assertThat(bulk).contains("/cdn/transparencia/");
    }

    @Test
    void dicionarioDadosEDescritivoHumanoIncluiMascaraFiltrosEOrdenacaoPadrao() {
        ResponseEntity<String> resposta = new TransparenciaPublicaController(consultar, totalizar, mapper).dicionarioDados();

        assertThat(resposta.getBody())
                .contains("***.456.***-**")
                .contains("Detalhe e totalização aceitam os mesmos parâmetros")
                .contains("mais recente primeiro");
    }

    private static PublicacaoTransparencia publicacao(UUID enteId) {
        return new PublicacaoTransparencia(
                new TenantId(enteId),
                "execucao.pagamento.registrado.v1",
                "execucao:pagamento:11111111-1111-1111-1111-111111111111",
                1,
                Instant.parse("2026-08-02T12:00:00Z"),
                Instant.parse("2026-08-03T23:59:59Z"),
                """
                {
                  "evento": "pagamento",
                  "pagamentoId": "11111111-1111-1111-1111-111111111111",
                  "fatoContabilId": "22222222-2222-2222-2222-222222222222",
                  "publicarAte": "2026-08-03T23:59:59Z",
                  "valor": "1200.00",
                  "historico": "Pagamento com CPF 12345678901 protegido",
                  "beneficiario": {
                    "nome": "Maria Silva",
                    "documento": "123.456.789-01",
                    "rg": "RG-ABCD",
                    "endereco": "Rua Interna, 10",
                    "contaBancaria": "001/12345-6"
                  },
                  "cnpj": "12345678000199",
                  "remuneracaoNominal": "9876.54"
                }
                """);
    }

    private static void assertPublicoSemPiiIntegra(String texto) {
        assertThat(texto)
                .doesNotContain("123.456.789-01")
                .doesNotContain("12345678901")
                .doesNotContain("RG-ABCD")
                .doesNotContain("Rua Interna")
                .doesNotContain("001/12345-6")
                .doesNotContain("contaBancaria");
    }
}
