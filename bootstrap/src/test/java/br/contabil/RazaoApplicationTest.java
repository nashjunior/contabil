package br.contabil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Smoke test do composition root SEM subir o contexto Spring — mantém o build
 * autocontido (sem dependência de Postgres). A validação end-to-end de
 * contexto + Flyway roda contra um Postgres real fora do ciclo de build.
 */
class RazaoApplicationTest {

    @Test
    void aplicacaoEhComposicaoRootSpringBoot() {
        assertThat(RazaoApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .as("o composition root deve ser anotado com @SpringBootApplication")
                .isTrue();
    }
}
