package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Referência identificada à unidade gestora responsável por um empenho. */
public record UnidadeGestoraId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public UnidadeGestoraId {
        Objects.requireNonNull(valor, "UnidadeGestoraId não pode ser nulo");
    }

    public static UnidadeGestoraId novo() {
        return new UnidadeGestoraId(UUID.randomUUID());
    }

    public static UnidadeGestoraId de(String uuid) {
        return new UnidadeGestoraId(UUID.fromString(uuid));
    }
}
