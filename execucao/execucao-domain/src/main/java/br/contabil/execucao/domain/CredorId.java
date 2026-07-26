package br.contabil.execucao.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Referência identificada ao credor/fornecedor (cadastro estruturante) de um empenho. */
public record CredorId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public CredorId {
        Objects.requireNonNull(valor, "CredorId não pode ser nulo");
    }

    public static CredorId novo() {
        return new CredorId(UUID.randomUUID());
    }

    public static CredorId de(String uuid) {
        return new CredorId(UUID.fromString(uuid));
    }
}
