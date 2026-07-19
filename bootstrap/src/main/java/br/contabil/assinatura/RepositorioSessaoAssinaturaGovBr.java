package br.contabil.assinatura;

import br.contabil.plataforma.domain.TenantId;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import org.springframework.web.util.WebUtils;

final class RepositorioSessaoAssinaturaGovBr {

    private static final String ATTR_FLUXO = "siafic.assinatura.govbr.oauth.fluxo";
    private static final String ATTR_TOKEN = "siafic.assinatura.govbr.oauth.token";

    private final SecureRandom secureRandom;
    private final Clock clock;
    private final AssinaturaGovBrOAuthProperties properties;

    RepositorioSessaoAssinaturaGovBr(SecureRandom secureRandom, Clock clock, AssinaturaGovBrOAuthProperties properties) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    FluxoPendente iniciar(HttpSession sessao, TenantId ente) {
        Objects.requireNonNull(sessao, "sessao");
        Objects.requireNonNull(ente, "ente");
        FluxoPendente fluxo = new FluxoPendente(
                tokenAleatorio(32),
                tokenAleatorio(64),
                ente,
                clock.instant().plus(properties.stateTtl()));
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
                throw new IllegalStateException("Fluxo OAuth2 de assinatura nao encontrado na sessao");
            }
            if (!fluxo.state().equals(state)) {
                throw new IllegalStateException("state OAuth2 invalido para a sessao de assinatura");
            }
            if (!fluxo.expiraEm().isAfter(clock.instant())) {
                throw new IllegalStateException("state OAuth2 expirado para a sessao de assinatura");
            }
            return fluxo;
        }
    }

    void guardarToken(HttpSession sessao, TenantId ente, AssinaturaGovBrOAuthToken token) {
        Objects.requireNonNull(sessao, "sessao");
        Objects.requireNonNull(ente, "ente");
        Objects.requireNonNull(token, "token");
        synchronized (WebUtils.getSessionMutex(sessao)) {
            sessao.setAttribute(ATTR_TOKEN, new TokenSessao(token.accessToken(), ente, token.expiraEm()));
        }
    }

    String tokenAtual(HttpSession sessao) {
        Objects.requireNonNull(sessao, "sessao");
        synchronized (WebUtils.getSessionMutex(sessao)) {
            Object valor = sessao.getAttribute(ATTR_TOKEN);
            if (!(valor instanceof TokenSessao token)) {
                throw new IllegalStateException("Signatario ainda nao concluiu OAuth2 gov.br para assinatura");
            }
            if (!token.expiraEm().isAfter(clock.instant())) {
                sessao.removeAttribute(ATTR_TOKEN);
                throw new IllegalStateException("Token OAuth2 gov.br da assinatura expirado");
            }
            return token.accessToken();
        }
    }

    private String tokenAleatorio(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    record FluxoPendente(String state, String codeVerifier, TenantId ente, Instant expiraEm)
            implements Serializable {

        private static final long serialVersionUID = 1L;

        FluxoPendente {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(codeVerifier, "codeVerifier");
            Objects.requireNonNull(ente, "ente");
            Objects.requireNonNull(expiraEm, "expiraEm");
        }
    }

    private record TokenSessao(String accessToken, TenantId ente, Instant expiraEm)
            implements Serializable {

        private static final long serialVersionUID = 1L;

        private TokenSessao {
            Objects.requireNonNull(accessToken, "accessToken");
            Objects.requireNonNull(ente, "ente");
            Objects.requireNonNull(expiraEm, "expiraEm");
        }
    }
}
