package br.contabil.razao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/** Identificador estável de um lançamento dentro de um fato contábil. */
public record LancamentoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public LancamentoId {
        Validacoes.exigirNaoNulo(valor, "LancamentoId");
    }

    public static LancamentoId novo() {
        return new LancamentoId(UUID.randomUUID());
    }

    public static LancamentoId de(String uuid) {
        return new LancamentoId(UUID.fromString(uuid));
    }
}
