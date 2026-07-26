package br.contabil.razao.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identificador estável de um lançamento dentro de um fato contábil. */
public record LancamentoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public LancamentoId {
        Objects.requireNonNull(valor, "LancamentoId não pode ser nulo");
    }

    public static LancamentoId novo() {
        return new LancamentoId(UUID.randomUUID());
    }

    public static LancamentoId de(String uuid) {
        return new LancamentoId(UUID.fromString(uuid));
    }
}
