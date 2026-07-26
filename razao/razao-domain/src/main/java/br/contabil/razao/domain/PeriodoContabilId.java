package br.contabil.razao.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identificador estável de um período contábil. */
public record PeriodoContabilId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public PeriodoContabilId {
        Objects.requireNonNull(valor, "PeriodoContabilId não pode ser nulo");
    }

    public static PeriodoContabilId novo() {
        return new PeriodoContabilId(UUID.randomUUID());
    }

    public static PeriodoContabilId de(String uuid) {
        return new PeriodoContabilId(UUID.fromString(uuid));
    }
}
