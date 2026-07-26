package br.contabil.plataforma.domain.consulta;

import java.time.Instant;
import java.util.Objects;

/** Recorte temporal semântico para consultas/read models. */
public record JanelaConsulta(Instant desde, Instant ate) {

    public JanelaConsulta {
        Objects.requireNonNull(desde, "início da janela");
        Objects.requireNonNull(ate, "fim da janela");
        if (!desde.isBefore(ate)) {
            throw new IllegalArgumentException("início da janela deve ser anterior ao fim");
        }
    }
}
