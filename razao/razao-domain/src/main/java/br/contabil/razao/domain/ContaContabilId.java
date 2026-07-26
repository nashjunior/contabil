package br.contabil.razao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/** Identificador estável de uma conta contábil PCASP. */
public record ContaContabilId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ContaContabilId {
        Validacoes.exigirNaoNulo(valor, "ContaContabilId");
    }

    public static ContaContabilId novo() {
        return new ContaContabilId(UUID.randomUUID());
    }

    public static ContaContabilId de(String uuid) {
        return new ContaContabilId(UUID.fromString(uuid));
    }
}
