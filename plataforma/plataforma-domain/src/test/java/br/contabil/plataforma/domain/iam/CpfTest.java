package br.contabil.plataforma.domain.iam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

class CpfTest {

    @Test
    @DisplayName("mascararOcorrencias troca CPFs crus em texto sem expor mais que 3 dígitos centrais")
    void mascararOcorrencias() {
        String texto = "gov.br recusou CPF 11122233344; titular 555.666.777-88 sem nível suficiente";

        String resultado = Cpf.mascararOcorrencias(texto);

        assertThat(resultado)
                .contains("***.222.***-**")
                .contains("***.666.***-**")
                .doesNotContain("11122233344")
                .doesNotContain("555.666.777-88");
    }
}
