package br.contabil.assinatura;

import java.time.Instant;
import java.util.Objects;

record AssinaturaGovBrOAuthToken(String accessToken, Instant expiraEm) {

    AssinaturaGovBrOAuthToken {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(expiraEm, "expiraEm");
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken nao pode ser vazio");
        }
    }
}
