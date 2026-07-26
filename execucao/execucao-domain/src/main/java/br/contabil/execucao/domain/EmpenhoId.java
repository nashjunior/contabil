package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/** Identificador estável do agregado Empenho, contrato mínimo para encadear a F1. */
public record EmpenhoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public EmpenhoId {
        Validacoes.exigirNaoNulo(valor, "EmpenhoId");
    }

    public static EmpenhoId novo() {
        return new EmpenhoId(UUID.randomUUID());
    }

    public static EmpenhoId de(String uuid) {
        return new EmpenhoId(UUID.fromString(uuid));
    }
}
