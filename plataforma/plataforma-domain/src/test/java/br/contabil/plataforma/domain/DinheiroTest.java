package br.contabil.plataforma.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DinheiroTest {

    @Test
    @DisplayName("normaliza para 2 casas decimais com HALF_EVEN")
    void normalizaEscala() {
        assertThat(Dinheiro.de("2.5").valor()).isEqualByComparingTo("2.50");
        assertThat(Dinheiro.de("1.005").valor()).isEqualByComparingTo("1.00"); // HALF_EVEN -> par
        assertThat(Dinheiro.de("1.015").valor()).isEqualByComparingTo("1.02"); // HALF_EVEN -> par
    }

    @Test
    @DisplayName("igualdade independe da escala de entrada")
    void igualdade() {
        assertThat(Dinheiro.de("10")).isEqualTo(Dinheiro.de("10.00"));
    }

    @Test
    @DisplayName("soma e subtração preservam a invariante decimal")
    void aritmetica() {
        assertThat(Dinheiro.de("0.10").somar(Dinheiro.de("0.20")))
                .isEqualTo(Dinheiro.de("0.30"));
        assertThat(Dinheiro.de("1.00").subtrair(Dinheiro.de("1.00")).isZero()).isTrue();
    }

    @Test
    @DisplayName("rejeita valor nulo")
    void rejeitaNulo() {
        assertThatNullPointerException().isThrownBy(() -> new Dinheiro((BigDecimal) null));
    }
}
