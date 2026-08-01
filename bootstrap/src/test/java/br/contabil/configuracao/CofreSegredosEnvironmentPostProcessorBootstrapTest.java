package br.contabil.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(
        classes = CofreSegredosEnvironmentPostProcessorBootstrapTest.AplicacaoMinima.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        args = {
                "--spring.datasource.password-ref=env:SIAFIC_F0_DB_RUNTIME_PASSWORD",
                "--spring.flyway.password-ref=env:SIAFIC_F0_DB_MIGRATION_PASSWORD"
        })
class CofreSegredosEnvironmentPostProcessorBootstrapTest {

    @Autowired
    private Environment environment;

    @Test
    void bootstrapDescobrePostProcessorDoModuloPlataformaInfra() {
        assertThat(environment.getProperty("spring.datasource.password"))
                .isEqualTo("nao-usado-dynamicpropertysource-sobrescreve");
        assertThat(environment.getProperty("spring.flyway.password"))
                .isEqualTo("nao-usado-dynamicpropertysource-sobrescreve");
    }

    @SpringBootConfiguration
    static class AplicacaoMinima {}
}
