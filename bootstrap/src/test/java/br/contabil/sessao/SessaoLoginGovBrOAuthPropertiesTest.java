package br.contabil.sessao;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class SessaoLoginGovBrOAuthPropertiesTest {

    @Test
    void configuracaoCompletaComTodasUrisHttpsNaoLancaExcecao() {
        var properties = propriedades(
                URI.create("https://sso.staging.acesso.gov.br/authorize"),
                URI.create("https://sso.staging.acesso.gov.br/token"),
                URI.create("https://siafic.exemplo.gov.br/sessao/oauth/callback"),
                List.of("openid", "profile"));

        assertThatCode(properties::exigirConfiguracaoCompleta).doesNotThrowAnyException();
    }

    @Test
    void escopoPadraoEOpenidProfile() {
        var properties = propriedades(
                URI.create("https://sso.staging.acesso.gov.br/authorize"),
                URI.create("https://sso.staging.acesso.gov.br/token"),
                URI.create("https://siafic.exemplo.gov.br/sessao/oauth/callback"),
                null);

        assertThat(properties.scopesComoParametro()).isEqualTo("openid profile");
    }

    @Test
    void escoposSignOuSignatureSessionSaoRecusados() {
        assertThatThrownBy(() -> propriedades(
                        URI.create("https://sso.staging.acesso.gov.br/authorize"),
                        URI.create("https://sso.staging.acesso.gov.br/token"),
                        URI.create("https://siafic.exemplo.gov.br/sessao/oauth/callback"),
                        List.of("sign")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sign");

        assertThatThrownBy(() -> propriedades(
                        URI.create("https://sso.staging.acesso.gov.br/authorize"),
                        URI.create("https://sso.staging.acesso.gov.br/token"),
                        URI.create("https://siafic.exemplo.gov.br/sessao/oauth/callback"),
                        List.of("openid", "signature_session")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signature_session");
    }

    @Test
    void redirectUriHttpEmLoopbackNaoLancaExcecao() {
        var properties = propriedades(
                URI.create("https://sso.staging.acesso.gov.br/authorize"),
                URI.create("https://sso.staging.acesso.gov.br/token"),
                URI.create("http://localhost:8080/sessao/oauth/callback"),
                List.of("openid", "profile"));

        assertThatCode(properties::exigirConfiguracaoCompleta).doesNotThrowAnyException();
    }

    @Test
    void redirectUriHttpForaDeLoopbackLancaExcecao() {
        var properties = propriedades(
                URI.create("https://sso.staging.acesso.gov.br/authorize"),
                URI.create("https://sso.staging.acesso.gov.br/token"),
                URI.create("http://siafic.exemplo.gov.br/sessao/oauth/callback"),
                List.of("openid", "profile"));

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redirect-uri")
                .hasMessageContaining("https");
    }

    @Test
    void semClientIdOuSecretLancaExcecao() {
        var properties = new SessaoLoginGovBrOAuthProperties(
                URI.create("https://sso.staging.acesso.gov.br/authorize"),
                URI.create("https://sso.staging.acesso.gov.br/token"),
                "",
                "",
                URI.create("https://siafic.exemplo.gov.br/sessao/oauth/callback"),
                null,
                List.of("openid", "profile"),
                Duration.ofMinutes(10));

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-id");
    }

    private static SessaoLoginGovBrOAuthProperties propriedades(
            URI authorizationUri, URI tokenUri, URI redirectUri, List<String> scopes) {
        return new SessaoLoginGovBrOAuthProperties(
                authorizationUri,
                tokenUri,
                "cliente-siafic-login",
                "segredo",
                redirectUri,
                null,
                scopes,
                Duration.ofMinutes(10));
    }
}
