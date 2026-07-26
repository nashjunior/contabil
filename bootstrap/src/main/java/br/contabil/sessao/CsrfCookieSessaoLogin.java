package br.contabil.sessao;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.http.ResponseCookie;

/**
 * Constantes e fábrica do cookie anti-CSRF de <i>double-submit</i> do BFF de login
 * (ADR-0035 §3/refinamento RAZ-127 §2). Não é {@code HttpOnly} — o SPA precisa lê-lo
 * para ecoar o valor no cabeçalho {@link #CABECALHO_CSRF} de toda chamada mutante
 * autenticada por cookie; {@link CsrfCookieProtecaoFilter} confere a igualdade.
 * Nenhum dado de identidade (PII) trafega neste cookie, só um nonce aleatório.
 */
final class CsrfCookieSessaoLogin {

    static final String NOME_COOKIE = "sessao-csrf";
    static final String CABECALHO_CSRF = "X-Csrf-Token";

    private CsrfCookieSessaoLogin() {}

    static ResponseCookie novoCookie(SecureRandom secureRandom) {
        byte[] buffer = new byte[32];
        secureRandom.nextBytes(buffer);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        return ResponseCookie.from(NOME_COOKIE, valor)
                .httpOnly(false)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .build();
    }
}
