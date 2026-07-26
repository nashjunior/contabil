package br.contabil.sessao;

import java.time.Instant;
import java.util.Objects;

/** Asserção gov.br (JWT bruto que {@code VerificadorJwtGovBr} aceita) trocada pelo {@code code} OIDC. */
record SessaoLoginGovBrOAuthToken(String assercao, Instant expiraEm) {

    SessaoLoginGovBrOAuthToken {
        Objects.requireNonNull(assercao, "assercao");
        Objects.requireNonNull(expiraEm, "expiraEm");
        if (assercao.isBlank()) {
            throw new IllegalArgumentException("assercao nao pode ser vazia");
        }
    }
}
