package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identificador estável do agregado Pagamento. */
public record PagamentoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public PagamentoId {
        Objects.requireNonNull(valor, "PagamentoId não pode ser nulo");
    }

    public static PagamentoId novo() {
        return new PagamentoId(UUID.randomUUID());
    }

    public static PagamentoId de(String uuid) {
        return new PagamentoId(UUID.fromString(uuid));
    }
}
