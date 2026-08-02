package br.contabil.execucao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class JanelaMandatoTest {

    private final JanelaMandato mandato = new JanelaMandato(LocalDate.of(2025, 1, 1), LocalDate.of(2028, 12, 31));

    @Test
    void rejeitaDataFimAnteriorOuIgualADataInicio() {
        assertThatThrownBy(() -> new JanelaMandato(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JanelaMandato(LocalDate.of(2025, 1, 1), LocalDate.of(2024, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void primeiroDeMaioDoAnoDoFimEstaNaJanela() {
        assertThat(mandato.estaNosUltimosDoisQuadrimestres(LocalDate.of(2028, 5, 1))).isTrue();
    }

    @Test
    void trintaEUmDeDezembroDoAnoDoFimEstaNaJanela() {
        assertThat(mandato.estaNosUltimosDoisQuadrimestres(LocalDate.of(2028, 12, 31))).isTrue();
    }

    @Test
    void trintaDeAbrilDoAnoDoFimEstaForaDaJanela() {
        assertThat(mandato.estaNosUltimosDoisQuadrimestres(LocalDate.of(2028, 4, 30))).isFalse();
    }

    @Test
    void anoAnteriorAoUltimoAnoDoMandatoEstaForaDaJanelaMesmoEmMaio() {
        assertThat(mandato.estaNosUltimosDoisQuadrimestres(LocalDate.of(2027, 5, 15))).isFalse();
    }

    @Test
    void anoPosteriorAoFimDoMandatoEstaForaDaJanela() {
        assertThat(mandato.estaNosUltimosDoisQuadrimestres(LocalDate.of(2029, 6, 1))).isFalse();
    }
}
