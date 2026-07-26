package br.contabil.razao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/** Identificador estável do agregado FatoContabil. */
public record FatoContabilId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public FatoContabilId {
        Validacoes.exigirNaoNulo(valor, "FatoContabilId");
    }

    public static FatoContabilId novo() {
        return new FatoContabilId(UUID.randomUUID());
    }

    public static FatoContabilId de(String uuid) {
        return new FatoContabilId(UUID.fromString(uuid));
    }
}
