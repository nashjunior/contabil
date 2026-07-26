package br.contabil.consulta;

import java.math.BigDecimal;

import br.contabil.execucao.domain.ExecucaoOrcamentariaPeriodo;

/** Corpo HTTP de {@code GET .../execucao/orcamentaria} — RAZ-101. */
record ExecucaoOrcamentariaResponse(
        int exercicio,
        int mes,
        BigDecimal totalEmpenhado,
        BigDecimal totalLiquidado,
        BigDecimal totalPago,
        BigDecimal saldoALiquidar,
        BigDecimal saldoAPagar) {

    static ExecucaoOrcamentariaResponse de(ExecucaoOrcamentariaPeriodo execucao) {
        return new ExecucaoOrcamentariaResponse(
                execucao.exercicio(),
                execucao.mes(),
                execucao.totalEmpenhado().valor(),
                execucao.totalLiquidado().valor(),
                execucao.totalPago().valor(),
                execucao.saldoALiquidar().valor(),
                execucao.saldoAPagar().valor());
    }
}
