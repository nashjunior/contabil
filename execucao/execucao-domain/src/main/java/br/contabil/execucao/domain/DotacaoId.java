package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/** Identificador estável do agregado Dotacao (vínculo orçamentário). */
public record DotacaoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public DotacaoId {
        Validacoes.exigirNaoNulo(valor, "DotacaoId");
    }

    public static DotacaoId novo() {
        return new DotacaoId(UUID.randomUUID());
    }

    public static DotacaoId de(String uuid) {
        return new DotacaoId(UUID.fromString(uuid));
    }
}
