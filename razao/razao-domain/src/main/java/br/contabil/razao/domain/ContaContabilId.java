package br.contabil.razao.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identificador estável de uma conta contábil PCASP. */
public record ContaContabilId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ContaContabilId {
        Objects.requireNonNull(valor, "ContaContabilId não pode ser nulo");
    }

    public static ContaContabilId novo() {
        return new ContaContabilId(UUID.randomUUID());
    }

    public static ContaContabilId de(String uuid) {
        return new ContaContabilId(UUID.fromString(uuid));
    }
}
