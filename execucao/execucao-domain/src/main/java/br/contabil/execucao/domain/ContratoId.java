package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Referência identificada ao contrato vinculado a um empenho (opcional). */
public record ContratoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ContratoId {
        Objects.requireNonNull(valor, "ContratoId não pode ser nulo");
    }

    public static ContratoId novo() {
        return new ContratoId(UUID.randomUUID());
    }

    public static ContratoId de(String uuid) {
        return new ContratoId(UUID.fromString(uuid));
    }
}
