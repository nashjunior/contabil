package br.contabil.sessao;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config do BFF de <b>login</b> OIDC gov.br (ADR-0035) — cliente e escopo distintos do
 * BFF de assinatura ({@code siafic.assinatura.govbr.oauth}, ADR-0017). Escopo mínimo
 * {@code openid profile}: nunca {@code sign}/{@code signature_session} (refinamento de
 * segurança RAZ-127 §3) — o compact constructor recusa esses escopos mesmo se alguém
 * configurar errado.
 */
@ConfigurationProperties(prefix = "siafic.login.govbr.oauth")
record SessaoLoginGovBrOAuthProperties(
        URI authorizationUri,
        URI tokenUri,
        String clientId,
        String clientSecret,
        URI redirectUri,
        URI postLoginRedirectUri,
        List<String> scopes,
        Duration stateTtl) {

    private static final URI AUTHORIZATION_URI_PADRAO = URI.create("https://sso.staging.acesso.gov.br/authorize");
    private static final URI TOKEN_URI_PADRAO = URI.create("https://sso.staging.acesso.gov.br/token");
    private static final URI POST_LOGIN_REDIRECT_URI_PADRAO = URI.create("/");
    private static final Duration STATE_TTL_PADRAO = Duration.ofMinutes(10);
    private static final List<String> SCOPES_PADRAO = List.of("openid", "profile");
    private static final List<String> SCOPES_PROIBIDOS = List.of("sign", "signature_session");

    SessaoLoginGovBrOAuthProperties {
        authorizationUri = authorizationUri == null ? AUTHORIZATION_URI_PADRAO : authorizationUri;
        tokenUri = tokenUri == null ? TOKEN_URI_PADRAO : tokenUri;
        clientId = clientId == null ? "" : clientId.trim();
        clientSecret = clientSecret == null ? "" : clientSecret;
        redirectUri = redirectUri == null ? null : redirectUri;
        postLoginRedirectUri = postLoginRedirectUri == null ? POST_LOGIN_REDIRECT_URI_PADRAO : postLoginRedirectUri;
        scopes = scopes == null || scopes.isEmpty() ? SCOPES_PADRAO : List.copyOf(scopes);
        stateTtl = stateTtl == null ? STATE_TTL_PADRAO : stateTtl;
        if (stateTtl.isNegative() || stateTtl.isZero()) {
            throw new IllegalArgumentException("stateTtl deve ser positivo");
        }
        for (String proibido : SCOPES_PROIBIDOS) {
            if (scopes.contains(proibido)) {
                throw new IllegalStateException(
                        "siafic.login.govbr.oauth.scopes nao pode incluir '" + proibido
                                + "' — escopo de login e de assinatura sao clientes/escopos distintos (ADR-0035 §3)");
            }
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
            throw new IllegalStateException("siafic.login.govbr.oauth." + nome + " deve ser URI absoluta");
        }
        if (!"https".equals(uri.getScheme()) && !isLoopback(uri.getHost())) {
            throw new IllegalStateException(
                    "siafic.login.govbr.oauth." + nome
                            + " deve usar https — client_secret/code/access_token trafegam nela em claro "
                            + "sobre http (excecao so para loopback em desenvolvimento local)");
        }
    }

    private static boolean isLoopback(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host);
    }

    private static void exigirTexto(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("siafic.login.govbr.oauth." + nome + " deve vir do cofre/ambiente");
        }
    }
}
