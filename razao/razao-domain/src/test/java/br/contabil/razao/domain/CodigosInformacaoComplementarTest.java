package br.contabil.razao.domain;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Cada IC de partida baseada em código (CO/NR/ND/FS/PO) segue o mesmo contrato de
 * {@link FonteRecurso}: normaliza com trim, rejeita vazio e respeita o teto de 20
 * do schema. AI (ano) tem contrato próprio (faixa numérica).
 */
class CodigosInformacaoComplementarTest {

    static Stream<Arguments> codigos() {
        return Stream.of(
                Arguments.of("CO", (Function<String, Object>) ExecucaoOrcamentaria::de),
                Arguments.of("NR", (Function<String, Object>) NaturezaReceita::de),
                Arguments.of("ND", (Function<String, Object>) NaturezaDespesa::de),
                Arguments.of("FS", (Function<String, Object>) FuncaoSubfuncao::de),
                Arguments.of("PO", (Function<String, Object>) PoderOrgao::de));
    }

    @ParameterizedTest(name = "{0}: normaliza com trim")
    @MethodSource("codigos")
    void normalizaComTrim(String sigla, Function<String, Object> fabrica) {
        assertThat(fabrica.apply("  123  ")).isEqualTo(fabrica.apply("123"));
    }

    @ParameterizedTest(name = "{0}: rejeita nulo/vazio/só espaços e acima do teto")
    @MethodSource("codigos")
    void rejeitaInvalidos(String sigla, Function<String, Object> fabrica) {
        assertThatThrownBy(() -> fabrica.apply(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> fabrica.apply("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fabrica.apply("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fabrica.apply("x".repeat(21))).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("AI: aceita ano plausível, rejeita fora da faixa")
    @org.junit.jupiter.api.Test
    void anoInscricaoRp() {
        assertThat(AnoInscricaoRp.de(2025).ano()).isEqualTo(2025);
        assertThatThrownBy(() -> AnoInscricaoRp.de(1900)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnoInscricaoRp.de(12345)).isInstanceOf(IllegalArgumentException.class);
    }
}
