package br.contabil.execucao.domain.repository;

import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.SaldoDotacao;
import br.contabil.execucao.domain.SaldoEmpenho;
import br.contabil.execucao.domain.SaldoLiquidacao;
import br.contabil.plataforma.domain.TenantId;

/** Port de consulta dos saldos acumulados da execução, derivado dos estágios já registrados. */
public interface SaldosExecucaoPort {

    /** RAZ-66: saldo disponível da dotação, para travar empenho <= crédito (art. 59). */
    SaldoDotacao saldoDotacao(TenantId enteId, DotacaoId dotacaoId);

    SaldoEmpenho saldoEmpenho(TenantId enteId, EmpenhoId empenhoId);

    SaldoLiquidacao saldoLiquidacao(TenantId enteId, LiquidacaoId liquidacaoId);
}
