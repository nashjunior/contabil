package br.contabil.razao.domain.repository;

import java.util.List;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FonteRecurso;

/**
 * Read-model derivado: saldo devedor líquido (Σdébito − Σcrédito) agrupado por
 * {@link FonteRecurso} para um conjunto de contas PCASP de controle (ADR-0054,
 * ADR-0007). Usado pelo gate art.42 (RAZ-207) e pelo gerador de MSC/IC FR
 * (RAZ-209).
 *
 * <p>Sem compensação entre fontes: o saldo de uma fonte nunca abate o de outra.
 * Fontes com saldo zero são omitidas.
 *
 * <p>O chamador fornece as {@code ContaContabilId}s das contas de controle que
 * compõem o saldo pretendido (ex.: só a conta DDR "disponível a comprometer",
 * ou todas as contas de obrigação — a semântica do que significa "disponível"
 * ou "comprometido" fica com o consumidor, não com este port). Isso evita
 * que o port conheça os códigos PCASP [REVALIDAR] e torna o adapter agnóstico
 * à edição do MCASP vigente.
 */
public interface DisponibilidadePorFontePort {

    /**
     * Retorna o saldo devedor líquido por fonte para as contas informadas.
     *
     * @param enteId  ente (tenant) da consulta
     * @param contas  contas cujo saldo será somado — normalmente um subconjunto
     *                das contas DDR classes 7/8 (ex.: só "disponível a comprometer")
     * @return lista de saldos por fonte; fontes sem lançamentos não aparecem
     */
    List<SaldoPorFonte> consultarSaldoPorFonte(TenantId enteId, List<ContaContabilId> contas);

    /** Saldo devedor líquido (D−C) de um conjunto de contas DDR para uma fonte. */
    record SaldoPorFonte(FonteRecurso fonte, Dinheiro saldoDevedorLiquido) {}
}
