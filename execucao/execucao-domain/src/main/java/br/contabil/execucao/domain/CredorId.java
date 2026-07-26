package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/** Referência identificada ao credor/fornecedor (cadastro estruturante) de um empenho. */
public record CredorId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public CredorId {
        Validacoes.exigirNaoNulo(valor, "CredorId");
    }

    public static CredorId novo() {
        return new CredorId(UUID.randomUUID());
    }

    public static CredorId de(String uuid) {
        return new CredorId(UUID.fromString(uuid));
    }
}
