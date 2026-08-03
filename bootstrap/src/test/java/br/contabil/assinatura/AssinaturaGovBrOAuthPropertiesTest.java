package br.contabil.assinatura;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import br.contabil.plataforma.domain.assinatura.ServicoAssinatura.NivelAssinatura;

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

    @Test
    void frontendRetornoUriAusenteLancaExcecao() {
        var properties = propriedades(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("https://siafic.exemplo.gov.br/assinatura/oauth/callback"),
                null);

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("frontend-retorno-uri");
    }

    @Test
    void frontendRetornoUriHttpForaDeLoopbackLancaExcecao() {
        var properties = propriedades(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("https://siafic.exemplo.gov.br/assinatura/oauth/callback"),
                URI.create("http://app.exemplo.gov.br/execucao/assinatura/retorno"));

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frontend-retorno-uri")
                .hasMessageContaining("https");
    }

    @Test
    void signESignatureSessionSaoMutuamenteExclusivos() {
        var properties = propriedades(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("https://siafic.exemplo.gov.br/assinatura/oauth/callback"),
                URI.create("https://app.exemplo.gov.br/execucao/assinatura/retorno"),
                List.of("sign", "signature_session"));

        assertThatThrownBy(properties::exigirConfiguracaoCompleta)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sign e signature_session");
    }

    @Test
    void escopoIcpBrasilDeterministicoSuportaQualificada() {
        var properties = propriedades(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("https://siafic.exemplo.gov.br/assinatura/oauth/callback"),
                URI.create("https://app.exemplo.gov.br/execucao/assinatura/retorno"),
                List.of("signature_session", "icp_brasil"));

        assertThatCode(properties::exigirConfiguracaoCompleta).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(properties.suportaNivel(NivelAssinatura.QUALIFICADA_ICP_BRASIL))
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(properties.suportaNivel(NivelAssinatura.AVANCADA_GOVBR))
                .isFalse();
    }

    @Test
    void escopoGovBrEIcpBrasilNaoContaComoQualificadaDeterministica() {
        var properties = propriedades(
                URI.create("https://cas.staging.iti.br/oauth2.0/authorize"),
                URI.create("https://cas.staging.iti.br/oauth2.0/accessToken"),
                URI.create("https://siafic.exemplo.gov.br/assinatura/oauth/callback"),
                URI.create("https://app.exemplo.gov.br/execucao/assinatura/retorno"),
                List.of("signature_session", "govbr", "icp_brasil"));

        assertThatCode(properties::exigirConfiguracaoCompleta).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(properties.suportaNivel(NivelAssinatura.QUALIFICADA_ICP_BRASIL))
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(properties.suportaNivel(NivelAssinatura.AVANCADA_GOVBR))
                .isTrue();
    }

    private static AssinaturaGovBrOAuthProperties propriedades(URI authorizationUri, URI tokenUri, URI redirectUri) {
        return propriedades(
                authorizationUri,
                tokenUri,
                redirectUri,
                URI.create("https://app.exemplo.gov.br/execucao/assinatura/retorno"));
    }

    private static AssinaturaGovBrOAuthProperties propriedades(
            URI authorizationUri, URI tokenUri, URI redirectUri, URI frontendRetornoUri) {
        return new AssinaturaGovBrOAuthProperties(
                authorizationUri,
                tokenUri,
                "cliente-siafic",
                "segredo",
                redirectUri,
                frontendRetornoUri,
                List.of("signature_session"),
                Duration.ofMinutes(10));
    }

    private static AssinaturaGovBrOAuthProperties propriedades(
            URI authorizationUri, URI tokenUri, URI redirectUri, URI frontendRetornoUri, List<String> scopes) {
        return new AssinaturaGovBrOAuthProperties(
                authorizationUri,
                tokenUri,
                "cliente-siafic",
                "segredo",
                redirectUri,
                frontendRetornoUri,
                scopes,
                Duration.ofMinutes(10));
    }
}
