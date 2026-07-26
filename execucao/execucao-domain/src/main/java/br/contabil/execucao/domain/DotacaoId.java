package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identificador estável do agregado Dotacao (vínculo orçamentário). */
public record DotacaoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public DotacaoId {
        Objects.requireNonNull(valor, "DotacaoId não pode ser nulo");
    }

    public static DotacaoId novo() {
        return new DotacaoId(UUID.randomUUID());
    }

    public static DotacaoId de(String uuid) {
        return new DotacaoId(UUID.fromString(uuid));
    }
}
