package br.contabil.plataforma.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidacoesTest {

    @Test
    @DisplayName("retorna o próprio valor quando não nulo")
    void retornaValorNaoNulo() {
        assertThat(Validacoes.exigirNaoNulo("x", "campo")).isEqualTo("x");
    }

    @Test
    @DisplayName("mensagem no masculino por padrão")
    void mensagemMasculinaPadrao() {
        assertThatNullPointerException()
                .isThrownBy(() -> Validacoes.exigirNaoNulo(null, "enteId"))
                .withMessage("enteId não pode ser nulo");
    }

    @Test
    @DisplayName("mensagem no feminino para campos conhecidos (dataFato, dataCompetencia, natureza...)")
    void mensagemFemininaCamposConhecidos() {
        assertThatNullPointerException()
                .isThrownBy(() -> Validacoes.exigirNaoNulo(null, "dataFato"))
                .withMessage("dataFato não pode ser nula");
        assertThatNullPointerException()
                .isThrownBy(() -> Validacoes.exigirNaoNulo(null, "dataCompetencia"))
                .withMessage("dataCompetencia não pode ser nula");
    }
}
