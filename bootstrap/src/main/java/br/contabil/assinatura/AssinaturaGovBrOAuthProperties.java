package br.contabil.assinatura;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "siafic.assinatura.govbr.oauth")
record AssinaturaGovBrOAuthProperties(
        URI authorizationUri,
        URI tokenUri,
        String clientId,
        String clientSecret,
        URI redirectUri,
        List<String> scopes,
        Duration stateTtl) {

    private static final URI AUTHORIZATION_URI_PADRAO = URI.create("https://cas.staging.iti.br/oauth2.0/authorize");
    private static final URI TOKEN_URI_PADRAO = URI.create("https://cas.staging.iti.br/oauth2.0/accessToken");
    private static final Duration STATE_TTL_PADRAO = Duration.ofMinutes(10);
    private static final List<String> SCOPES_PADRAO = List.of("sign", "signature_session");

    AssinaturaGovBrOAuthProperties {
        authorizationUri = authorizationUri == null ? AUTHORIZATION_URI_PADRAO : authorizationUri;
        tokenUri = tokenUri == null ? TOKEN_URI_PADRAO : tokenUri;
        clientId = clientId == null ? "" : clientId.trim();
        clientSecret = clientSecret == null ? "" : clientSecret;
        redirectUri = redirectUri == null ? null : redirectUri;
        scopes = scopes == null || scopes.isEmpty() ? SCOPES_PADRAO : List.copyOf(scopes);
        stateTtl = stateTtl == null ? STATE_TTL_PADRAO : stateTtl;
        if (stateTtl.isNegative() || stateTtl.isZero()) {
            throw new IllegalArgumentException("stateTtl deve ser positivo");
        }
    }

    void exigirConfiguracaoCompleta() {
        exigirAbsoluta(authorizationUri, "authorization-uri");
        exigirAbsoluta(tokenUri, "token-uri");
        exigirAbsoluta(redirectUri, "redirect-uri");
        exigirTexto(clientId, "client-id");
        exigirTexto(clientSecret, "client-secret");
    }

    String scopesComoParametro() {
        return String.join(" ", scopes);
    }

    private static void exigirAbsoluta(URI uri, String nome) {
        Objects.requireNonNull(uri, nome);
        if (!uri.isAbsolute()) {
            throw new IllegalStateException("siafic.assinatura.govbr.oauth." + nome + " deve ser URI absoluta");
        }
        if (!"https".equals(uri.getScheme()) && !isLoopback(uri.getHost())) {
            throw new IllegalStateException(
                    "siafic.assinatura.govbr.oauth." + nome
                            + " deve usar https — client_secret/code/access_token trafegam nela em claro "
                            + "sobre http (excecao so para loopback em desenvolvimento local)");
        }
    }

    private static boolean isLoopback(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host);
    }

    private static void exigirTexto(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("siafic.assinatura.govbr.oauth." + nome + " deve vir do cofre/ambiente");
        }
    }
}
