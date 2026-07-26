package br.contabil.plataforma.infra.cofre;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import br.contabil.plataforma.domain.cofre.CofreSegredos;

class CofreSegredosEnvironmentPostProcessorTest {

    @Test
    void resolveReferenciaDeCofreComoPropriedadeRuntime() {
        var environment = new MockEnvironment()
                .withProperty("spring.datasource.password-ref", "cofre://siafic/f0/db/runtime/password");

        new CofreSegredosEnvironmentPostProcessor(cofreFake("senha-resolvida"))
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("senha-resolvida");
    }

    @Test
    void resolveReferenciaDeCofreDoClientSecretDeLoginGovBr() {
        var environment = new MockEnvironment()
                .withProperty(
                        "siafic.login.govbr.oauth.client-secret-ref",
                        "cofre://siafic/f0/login/govbr/oauth/client-secret");

        new CofreSegredosEnvironmentPostProcessor(cofreFake("segredo-login-resolvido"))
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("siafic.login.govbr.oauth.client-secret"))
                .isEqualTo("segredo-login-resolvido");
    }

    @Test
    void recusaValorDiretoQuandoExisteReferenciaDeCofre() {
        var environment = new MockEnvironment()
                .withProperty("spring.datasource.password-ref", "env:DB_RUNTIME_PASSWORD")
                .withProperty("spring.datasource.password", "senha-direta");

        assertThatIllegalStateException()
                .isThrownBy(() -> new CofreSegredosEnvironmentPostProcessor()
                        .postProcessEnvironment(environment, new SpringApplication()))
                .withMessageContaining("segredos runtime devem vir apenas do cofre");
    }

    @Test
    void ignoraQuandoReferenciaNaoFoiConfigurada() {
        var environment = new MockEnvironment();

        new CofreSegredosEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.password")).isNull();
    }

    private static CofreSegredos cofreFake(String valor) {
        return new CofreSegredos() {
            @Override
            public ValorSegredo obter(ContaServico conta, ReferenciaSegredo segredo) {
                return new ValorSegredo(valor.toCharArray(), Instant.parse("2026-07-19T12:15:00Z"));
            }

            @Override
            public void rotacionar(ReferenciaSegredo segredo) {}
        };
    }
}
