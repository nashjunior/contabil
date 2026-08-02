package br.contabil.razao.domain;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InformacoesComplementaresTest {

    @Test
    @DisplayName("nenhuma() é vazia e sem IC presentes")
    void nenhumaEhVazia() {
        InformacoesComplementares ic = InformacoesComplementares.nenhuma();
        assertThat(ic.vazia()).isTrue();
        assertThat(ic.presentes()).isEmpty();
    }

    @Test
    @DisplayName("apenasFonteRecurso(null) colapsa para nenhuma()")
    void apenasFonteRecursoNulaColapsa() {
        assertThat(InformacoesComplementares.apenasFonteRecurso(null))
                .isEqualTo(InformacoesComplementares.nenhuma());
    }

    @Test
    @DisplayName("de(...) só com nulos colapsa para nenhuma()")
    void deTudoNuloColapsa() {
        assertThat(InformacoesComplementares.de(null, null, null, null, null, null))
                .isEqualTo(InformacoesComplementares.nenhuma());
    }

    @Test
    @DisplayName("presentes() reporta exatamente as IC de partida preenchidas")
    void presentesReportaPreenchidas() {
        InformacoesComplementares ic = InformacoesComplementares.de(
                FonteRecurso.de("1500"),
                null,
                NaturezaReceita.de("11130111"),
                null,
                FuncaoSubfuncao.de("04122"),
                null);

        assertThat(ic.presentes())
                .isEqualTo(Set.of(InformacaoComplementar.FR, InformacaoComplementar.NR, InformacaoComplementar.FS));
        assertThat(ic.vazia()).isFalse();
    }

    @Test
    @DisplayName("comFonteRecurso substitui a FR preservando as demais IC")
    void comFonteRecursoSubstitui() {
        InformacoesComplementares base = InformacoesComplementares.de(
                FonteRecurso.de("1500"), ExecucaoOrcamentaria.de("2"), null, null, null, null);

        InformacoesComplementares trocada = base.comFonteRecurso(FonteRecurso.de("1600"));

        assertThat(trocada.fonteRecurso()).isEqualTo(FonteRecurso.de("1600"));
        assertThat(trocada.execucaoOrcamentaria()).isEqualTo(ExecucaoOrcamentaria.de("2"));
    }

    @Test
    @DisplayName("presentes() nunca inclui PO/FP/DC (não são IC de partida)")
    void presentesNuncaIncluiDimensoesNaoDePartida() {
        InformacoesComplementares ic = InformacoesComplementares.apenasFonteRecurso(FonteRecurso.de("1500"));
        assertThat(ic.presentes())
                .doesNotContain(InformacaoComplementar.PO, InformacaoComplementar.FP, InformacaoComplementar.DC);
    }
}
