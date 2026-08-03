package br.contabil.prestacaocontas.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.prestacaocontas.domain.CampoLayoutRemessaSimTceCe;
import br.contabil.prestacaocontas.domain.DimensaoOrganizacionalSimTceCe;
import br.contabil.prestacaocontas.domain.LayoutRemessaSimTceCe;
import br.contabil.prestacaocontas.domain.RemessaSimTceCe;
import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.LinhaBalancete;

class RemessaSimTceCeArquivoAdapterTest {

    private final TenantId enteId = new TenantId(UUID.randomUUID());
    private final LayoutRemessaSimTceCe layout = new LayoutRemessaSimTceCe(
            "308",
            "BA",
            "BAL",
            ",",
            25,
            List.of(
                    CampoLayoutRemessaSimTceCe.origem("tipo_registro", "tabela.codigo"),
                    CampoLayoutRemessaSimTceCe.origem("exercicio", "competencia.exercicio"),
                    CampoLayoutRemessaSimTceCe.origem("mes", "competencia.mes"),
                    CampoLayoutRemessaSimTceCe.origem("uo", "uo.codigo"),
                    CampoLayoutRemessaSimTceCe.origem("upc", "upc.codigo"),
                    CampoLayoutRemessaSimTceCe.origem("ug", "ug.codigo"),
                    CampoLayoutRemessaSimTceCe.origem("conta", "conta.codigo"),
                    CampoLayoutRemessaSimTceCe.origem("descricao", "conta.descricao"),
                    CampoLayoutRemessaSimTceCe.origem("natureza", "conta.natureza_saldo"),
                    CampoLayoutRemessaSimTceCe.origem("saldo_anterior", "saldo.anterior"),
                    CampoLayoutRemessaSimTceCe.origem("movimento_debito", "movimento.debito"),
                    CampoLayoutRemessaSimTceCe.origem("movimento_credito", "movimento.credito"),
                    CampoLayoutRemessaSimTceCe.origem("saldo_atual", "saldo.atual"),
                    CampoLayoutRemessaSimTceCe.fixo("campo_14", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_15", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_16", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_17", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_18", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_19", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_20", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_21", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_22", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_23", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_24", ""),
                    CampoLayoutRemessaSimTceCe.fixo("campo_25", "")));

    @Test
    @DisplayName("gera BAaaaamm.BAL ASCII com 25 campos por UO/UPC/UG e empacota no ZIP")
    void geraTabela308BalancetePorDimensaoOrganizacional() throws Exception {
        Balancete balancete = new Balancete(
                enteId,
                2026,
                3,
                List.of(new LinhaBalancete(
                        new ContaContabilId(UUID.randomUUID()),
                        "6.2.2.1.1",
                        "Credito disponível",
                        "D",
                        Dinheiro.de("100.00"),
                        Dinheiro.de("50.00"),
                        Dinheiro.de("20.00"),
                        Dinheiro.de("130.00"))));
        List<DimensaoOrganizacionalSimTceCe> dimensoes =
                List.of(new DimensaoOrganizacionalSimTceCe("0101", "000001", "00000001"));

        RemessaSimTceCe remessa = new RemessaSimTceCeArquivoAdapter(layout).gerarTabela308(balancete, dimensoes);

        assertThat(remessa.nomeArquivoPgi()).isEqualTo("BA202603.BAL");
        assertThat(remessa.nomeArquivoZip()).isEqualTo("BA202603.zip");

        String pgi = new String(remessa.conteudoPgi(), StandardCharsets.US_ASCII);
        String linha = pgi.stripTrailing();
        assertThat(linha.split(",", -1)).hasSize(25);
        assertThat(linha).startsWith("308,2026,03,0101,000001,00000001,6.2.2.1.1,Credito disponivel,D,100.00,50.00,20.00,130.00");

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(remessa.conteudoZip()))) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("BA202603.BAL");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo(pgi);
        }
    }

    @Test
    @DisplayName("rejeita remessa sem dimensão UO/UPC/UG")
    void rejeitaSemDimensaoOrganizacional() {
        Balancete balancete = new Balancete(enteId, 2026, 3, List.of());

        assertThatThrownBy(() -> new RemessaSimTceCeArquivoAdapter(layout).gerarTabela308(balancete, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UO/UPC/UG");
    }
}
