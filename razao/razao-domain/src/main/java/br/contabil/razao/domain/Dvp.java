package br.contabil.razao.domain;

import java.util.List;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;

/** Demonstração das Variações Patrimoniais, derivada do razão (ADR-0047). */
public record Dvp(TenantId enteId, int exercicio, List<LinhaDemonstracaoDcasp> linhas) {

    public Dvp {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(linhas, "linhas");
        linhas = List.copyOf(linhas);
    }
}
