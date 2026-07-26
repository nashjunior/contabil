package br.contabil.execucao.domain.repository;

import br.contabil.execucao.domain.ExecucaoOrcamentariaPeriodo;
import br.contabil.plataforma.domain.TenantId;

/**
 * Consulta agregada da execução orçamentária (empenhado/liquidado/pago) de um
 * ente, acumulada do início do exercício até o mês informado — read-model
 * DERIVADO de {@code empenho}/{@code liquidacao}/{@code pagamento}, mesmo
 * princípio de {@link SaldosExecucaoPort}.
 */
public interface ExecucaoOrcamentariaPeriodoPort {

    ExecucaoOrcamentariaPeriodo execucaoDoPeriodo(TenantId enteId, int exercicio, int mes);
}
