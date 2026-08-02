package br.contabil.sessao;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Prova o gate de duas flags do dev-IdP (RAZ-228/ADR-0052 item 1) na ponta de wiring do
 * Spring, sem subir a aplicação inteira (sem Postgres/Testcontainers): nem {@link
 * AssinadorJwtDevIdp} nem {@link SessaoDevIdpController} viram bean a menos que AMBAS
 * {@code siafic.dev-idp.enabled} e {@code siafic.iam.enabled} estejam {@code true} —
 * fail-closed por ausência da rota, não por checagem em runtime.
 */
class SessaoDevIdpConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(SessaoDevIdpConfiguration.class, SessaoDevIdpController.class);

    @Test
    void semNenhumaFlagNenhumBeanEhCriado() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AssinadorJwtDevIdp.class);
            assertThat(context).doesNotHaveBean(SessaoDevIdpController.class);
        });
    }

    @Test
    void soComDevIdpEnabledSemIamEnabledNenhumBeanEhCriado() {
        contextRunner.withPropertyValues("siafic.dev-idp.enabled=true").run(context -> {
            assertThat(context).doesNotHaveBean(AssinadorJwtDevIdp.class);
            assertThat(context).doesNotHaveBean(SessaoDevIdpController.class);
        });
    }

    @Test
    void soComIamEnabledSemDevIdpEnabledNenhumBeanEhCriado() {
        contextRunner.withPropertyValues("siafic.iam.enabled=true").run(context -> {
            assertThat(context).doesNotHaveBean(AssinadorJwtDevIdp.class);
            assertThat(context).doesNotHaveBean(SessaoDevIdpController.class);
        });
    }

    @Test
    void comAmbasAsFlagsEParDeChavesCompletoOsBeansSaoCriados() throws Exception {
        KeyPair par = gerarParDeChaves();
        contextRunner
                .withPropertyValues(
                        "siafic.dev-idp.enabled=true",
                        "siafic.iam.enabled=true",
                        "siafic.dev-idp.issuer=https://e2e.siafic.local/idp",
                        "siafic.dev-idp.audience=siafic-e2e",
                        "siafic.dev-idp.public-key-pem=" + pem(par.getPublic().getEncoded(), "PUBLIC"),
                        "siafic.dev-idp.private-key-pem=" + pem(par.getPrivate().getEncoded(), "PRIVATE"))
                .run(context -> {
                    assertThat(context).hasSingleBean(AssinadorJwtDevIdp.class);
                    assertThat(context).hasSingleBean(SessaoDevIdpController.class);
                });
    }

    @Test
    void comAmbasAsFlagsMasChavePrivadaAusenteOContextoFalhaAoSubir() {
        contextRunner
                .withPropertyValues(
                        "siafic.dev-idp.enabled=true",
                        "siafic.iam.enabled=true",
                        "siafic.dev-idp.issuer=https://e2e.siafic.local/idp",
                        "siafic.dev-idp.audience=siafic-e2e",
                        "siafic.dev-idp.public-key-pem=qualquer-coisa")
                .run(context -> assertThat(context).hasFailed());
    }

    private static KeyPair gerarParDeChaves() throws Exception {
        KeyPairGenerator geradorDeChaves = KeyPairGenerator.getInstance("RSA");
        geradorDeChaves.initialize(2048);
        return geradorDeChaves.generateKeyPair();
    }

    private static String pem(byte[] der, String tipo) {
        return "-----BEGIN " + tipo + " KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + tipo + " KEY-----";
    }
}
