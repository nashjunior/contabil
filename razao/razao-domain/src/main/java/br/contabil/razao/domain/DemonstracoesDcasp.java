package br.contabil.razao.domain;

import java.util.Objects;

import br.contabil.plataforma.domain.TenantId;

/**
 * Conjunto das quatro demonstrações DCASP obrigatórias para um exercício.
 * Read model reconstruível a partir dos fatos do razão, nunca snapshot fonte da
 * verdade (ADR-0047).
 */
public record DemonstracoesDcasp(
        TenantId enteId,
        int exercicio,
        BalancoOrcamentario balancoOrcamentario,
        BalancoFinanceiro balancoFinanceiro,
        BalancoPatrimonial balancoPatrimonial,
        Dvp dvp) {

    public DemonstracoesDcasp {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(balancoOrcamentario, "balancoOrcamentario");
        Objects.requireNonNull(balancoFinanceiro, "balancoFinanceiro");
        Objects.requireNonNull(balancoPatrimonial, "balancoPatrimonial");
        Objects.requireNonNull(dvp, "dvp");
    }
}
