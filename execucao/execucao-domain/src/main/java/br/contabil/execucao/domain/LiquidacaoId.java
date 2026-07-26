package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/** Identificador estável do agregado Liquidacao. */
public record LiquidacaoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public LiquidacaoId {
        Validacoes.exigirNaoNulo(valor, "LiquidacaoId");
    }

    public static LiquidacaoId novo() {
        return new LiquidacaoId(UUID.randomUUID());
    }

    public static LiquidacaoId de(String uuid) {
        return new LiquidacaoId(UUID.fromString(uuid));
    }
}
