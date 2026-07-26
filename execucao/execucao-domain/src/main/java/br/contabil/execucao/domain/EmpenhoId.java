package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identificador estável do agregado Empenho, contrato mínimo para encadear a F1. */
public record EmpenhoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public EmpenhoId {
        Objects.requireNonNull(valor, "EmpenhoId não pode ser nulo");
    }

    public static EmpenhoId novo() {
        return new EmpenhoId(UUID.randomUUID());
    }

    public static EmpenhoId de(String uuid) {
        return new EmpenhoId(UUID.fromString(uuid));
    }
}
