package br.contabil.sessao;

import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpSession;

/**
 * Guarda o {@code state}+{@code code_verifier} PKCE do fluxo OIDC de <b>login</b>
 * pendente na {@code HttpSession}, entre {@code /iniciar} e {@code /callback}
 * (ADR-0035). Diferente do fluxo de assinatura ({@code RepositorioSessaoAssinaturaGovBr}),
 * não carrega {@code ente} — o login é o próprio ato que resolve identidade/ente
 * (via {@code ServicoIdentidade.autenticar} no callback); não há sessão autenticada
 * anterior de onde lê-lo.
 */
final class RepositorioFluxoLoginGovBr {

    private static final String ATTR_FLUXO = "siafic.login.govbr.oauth.fluxo";

    private final SecureRandom secureRandom;
    private final Clock clock;
    private final SessaoLoginGovBrOAuthProperties properties;

    RepositorioFluxoLoginGovBr(SecureRandom secureRandom, Clock clock, SessaoLoginGovBrOAuthProperties properties) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    FluxoPendente iniciar(HttpSession sessao) {
        Objects.requireNonNull(sessao, "sessao");
        FluxoPendente fluxo = new FluxoPendente(
                tokenAleatorio(32), tokenAleatorio(64), clock.instant().plus(properties.stateTtl()));
        synchronized (WebUtils.getSessionMutex(sessao)) {
            sessao.setAttribute(ATTR_FLUXO, fluxo);
        }
        return fluxo;
    }

    FluxoPendente consumirFluxo(HttpSession sessao, String state) {
        Objects.requireNonNull(sessao, "sessao");
        Objects.requireNonNull(state, "state");
        synchronized (WebUtils.getSessionMutex(sessao)) {
            Object valor = sessao.getAttribute(ATTR_FLUXO);
            sessao.removeAttribute(ATTR_FLUXO);
            if (!(valor instanceof FluxoPendente fluxo)) {
                throw new IllegalStateException("Fluxo OIDC de login nao encontrado na sessao");
            }
            if (!fluxo.state().equals(state)) {
                throw new IllegalStateException("state OIDC invalido para a sessao de login");
            }
            if (!fluxo.expiraEm().isAfter(clock.instant())) {
                throw new IllegalStateException("state OIDC expirado para a sessao de login");
            }
            return fluxo;
        }
    }

    private String tokenAleatorio(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    record FluxoPendente(String state, String codeVerifier, Instant expiraEm) implements Serializable {

        private static final long serialVersionUID = 1L;

        FluxoPendente {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(codeVerifier, "codeVerifier");
            Objects.requireNonNull(expiraEm, "expiraEm");
        }
    }
}
