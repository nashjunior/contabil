package br.contabil.consulta;

import br.contabil.execucao.domain.ExecucaoOrcamentariaPeriodo;
import br.contabil.plataforma.domain.Dinheiro;

/**
 * Corpo HTTP de {@code GET .../execucao/orcamentaria} — RAZ-101. Os totais são
 * {@link Dinheiro} e o {@link DinheiroJacksonModule} os serializa como string
 * decimal de 2 casas (RAZ-79 §6.1 / ADR-0030 §2).
 */
record ExecucaoOrcamentariaResponse(
        int exercicio,
        int mes,
        Dinheiro totalEmpenhado,
        Dinheiro totalLiquidado,
        Dinheiro totalPago,
        Dinheiro saldoALiquidar,
        Dinheiro saldoAPagar) {

    static ExecucaoOrcamentariaResponse de(ExecucaoOrcamentariaPeriodo execucao) {
        return new ExecucaoOrcamentariaResponse(
                execucao.exercicio(),
                execucao.mes(),
                execucao.totalEmpenhado(),
                execucao.totalLiquidado(),
                execucao.totalPago(),
                execucao.saldoALiquidar(),
                execucao.saldoAPagar());
    }
}
