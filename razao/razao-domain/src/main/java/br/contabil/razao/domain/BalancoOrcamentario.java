package br.contabil.razao.domain;

import java.util.List;
import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;

/** Balanço Orçamentário DCASP, derivado do razão (ADR-0047). */
public record BalancoOrcamentario(TenantId enteId, int exercicio, List<LinhaDemonstracaoDcasp> linhas) {

    public BalancoOrcamentario {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(linhas, "linhas");
        linhas = List.copyOf(linhas);
    }
}
