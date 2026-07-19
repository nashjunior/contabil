package br.contabil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Smoke test do composition root SEM subir o contexto Spring — mantém este
 * teste autocontido (sem dependência de Postgres). Guardrails de schema/FK
 * (ver {@code br.contabil.migration}) usam Testcontainers e são pulados
 * automaticamente sem Docker; a validação end-to-end de contexto Spring +
 * Flyway completo ainda roda fora do ciclo de build.
 */
class RazaoApplicationTest {

    @Test
    void aplicacaoEhComposicaoRootSpringBoot() {
        assertThat(RazaoApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .as("o composition root deve ser anotado com @SpringBootApplication")
                .isTrue();
    }
}
