package br.contabil.assinatura;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssinaturaGovBrOAuthPropertiesTest {

    @Test
    void configuracaoCompletaComTodasUrisHttpsNaoLancaExcecao() {
        var properties = propriedades(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("https://siafic.exemplo.gov.br/assinatura/oauth/callback"));

        assertThatCode(properties::exigirConfiguracaoCompleta).doesNotThrowAnyException();
    }

    @Test
    void redirectUriHttpEmLoopbackNaoLancaExcecao() {
        var properties = propriedades(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("http://localhost:8080/assinatura/oauth/callback"));

        assertThatCode(properties::exigirConfiguracaoCompleta).doesNotThrowAnyException();

        var propertiesIp = propriedades(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("http://127.0.0.1:8080/assinatura/oauth/callback"));

        assertThatCode(propertiesIp::exigirConfiguracaoCompleta).doesNotThrowAnyException();
    }

    @Test
    void redirectUriHttpForaDeLoopbackLancaExcecao() {
        var properties = propriedades(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("http://siafic.exemplo.gov.br/assinatura/oauth/callback"));

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redirect-uri")
                .hasMessageContaining("https");
    }

    @Test
    void authorizationUriHttpForaDeLoopbackLancaExcecao() {
        var properties = propriedades(
                URI.create("http://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("https://siafic.exemplo.gov.br/assinatura/oauth/callback"));

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authorization-uri");
    }

    @Test
    void tokenUriHttpForaDeLoopbackLancaExcecao() {
        var properties = propriedades(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("http://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("https://siafic.exemplo.gov.br/assinatura/oauth/callback"));

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token-uri");
    }

    private static AssinaturaGovBrOAuthProperties propriedades(URI authorizationUri, URI tokenUri, URI redirectUri) {
        return new AssinaturaGovBrOAuthProperties(
                authorizationUri,
                tokenUri,
                "cliente-siafic",
                "segredo",
                redirectUri,
                List.of("sign", "signature_session"),
                Duration.ofMinutes(10));
    }
}
