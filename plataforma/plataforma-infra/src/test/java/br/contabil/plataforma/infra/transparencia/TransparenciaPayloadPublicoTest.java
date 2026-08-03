package br.contabil.plataforma.infra.transparencia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class TransparenciaPayloadPublicoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void suprimeCamposSensiveisMascaraCpfEPreservaCnpjERemuneracaoNominalPermitidos() {
        ObjectNode sanitizado = TransparenciaPayloadPublico.sanitizar(mapper, """
                {
                  "evento": "pagamento",
                  "pagamentoId": "11111111-1111-1111-1111-111111111111",
                  "fatoContabilId": "22222222-2222-2222-2222-222222222222",
                  "publicarAte": "2026-08-03T23:59:59Z",
                  "beneficiario": {
                    "nome": "Maria Silva",
                    "documento": "123.456.789-01",
                    "rg": "RG-ABCD",
                    "endereco": "Rua Interna, 10",
                    "contaBancaria": "001/12345-6"
                  },
                  "cnpj": "12345678000199",
                  "remuneracaoNominal": "12345.67"
                }
                """, "execucao.pagamento.registrado.v1");

        String json = sanitizado.toString();
        assertThat(json).contains("***.456.***-**");
        assertThat(json).contains("12345678000199");
        assertThat(json).contains("12345.67");
        assertThat(json).contains("\"estagio\":\"pago\"");
        assertThat(json).doesNotContain("123.456.789-01", "RG-ABCD", "Rua Interna", "001/12345-6", "contaBancaria");
    }

    @Test
    void mascaraCpfEmTextoLivreMesmoSemPontuacaoPadrao() {
        ObjectNode sanitizado = TransparenciaPayloadPublico.sanitizar(mapper, """
                {
                  "evento": "empenho",
                  "historico": "Pagamento ao munícipe CPF 123456789-01 conforme requerimento; segunda via CPF 123 456 789 01 anexa",
                  "numeroSequencial": "00012345",
                  "valor": "1520.35"
                }
                """, "execucao.empenho.registrado.v1");

        String json = sanitizado.toString();
        assertThat(json).doesNotContain("123456789-01", "123 456 789 01");
        assertThat(json).contains("***.456.***-**");
        assertThat(json).contains("00012345");
        assertThat(json).contains("1520.35");
    }
}
