package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/** Identificador estável do agregado Pagamento. */
public record PagamentoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public PagamentoId {
        Validacoes.exigirNaoNulo(valor, "PagamentoId");
    }

    public static PagamentoId novo() {
        return new PagamentoId(UUID.randomUUID());
    }

    public static PagamentoId de(String uuid) {
        return new PagamentoId(UUID.fromString(uuid));
    }
}
