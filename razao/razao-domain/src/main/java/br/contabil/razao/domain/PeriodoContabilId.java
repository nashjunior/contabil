package br.contabil.razao.domain;

import java.io.Serializable;
import java.util.UUID;

import br.contabil.plataforma.domain.Validacoes;

/** Identificador estável de um período contábil. */
public record PeriodoContabilId(UUID valor) implements Serializable {

    private static final long serialVersionUID = 1L;

    public PeriodoContabilId {
        Validacoes.exigirNaoNulo(valor, "PeriodoContabilId");
    }

    public static PeriodoContabilId novo() {
        return new PeriodoContabilId(UUID.randomUUID());
    }

    public static PeriodoContabilId de(String uuid) {
        return new PeriodoContabilId(UUID.fromString(uuid));
    }
}
