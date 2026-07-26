package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/** Referência identificada ao contrato vinculado a um empenho (opcional). */
public record ContratoId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ContratoId {
        Validacoes.exigirNaoNulo(valor, "ContratoId");
    }

    public static ContratoId novo() {
        return new ContratoId(UUID.randomUUID());
    }

    public static ContratoId de(String uuid) {
        return new ContratoId(UUID.fromString(uuid));
    }
}
