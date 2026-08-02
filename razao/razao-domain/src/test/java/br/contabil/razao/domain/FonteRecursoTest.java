package br.contabil.razao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FonteRecursoTest {

    @Test
    @DisplayName("normaliza o código com trim")
    void normalizaComTrim() {
        assertThat(FonteRecurso.de("  1500  ").codigo()).isEqualTo("1500");
    }

    @Test
    @DisplayName("rejeita código nulo, vazio ou só espaços")
    void rejeitaCodigoVazio() {
        assertThatThrownBy(() -> FonteRecurso.de(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FonteRecurso.de("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FonteRecurso.de("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejeita código acima do teto de 20 caracteres do schema")
    void rejeitaCodigoAcimaDoTeto() {
        assertThatThrownBy(() -> FonteRecurso.de("x".repeat(21)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(FonteRecurso.de("x".repeat(20)).codigo()).hasSize(20);
    }

    @Test
    @DisplayName("igualdade por valor do código (record)")
    void igualdadePorValor() {
        assertThat(FonteRecurso.de("1500")).isEqualTo(FonteRecurso.de("1500"));
        assertThat(FonteRecurso.de("1500")).isNotEqualTo(FonteRecurso.de("1600"));
    }
}
