package br.contabil.razao.domain.repository;

import java.util.List;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.LinhaMatrizSaldos;

/**
 * Projeção da MSC (Matriz de Saldos Contábeis, Portaria STN 642/2019): agregado
 * conta PCASP último nível × IC × NATUREZA × VALOR de um período — extensão de
 * {@link BalancetePort} pela dimensão das informações complementares (IC) de
 * partida (FR/CO/NR/ND/FS/AI) e fato-wide (PO), ADR-0056/ADR-0057. Read-model
 * DERIVADO dos lançamentos (ADR-0007), mesmo princípio de {@link BalancetePort}:
 * nunca uma tabela materializada, sempre reconstruível do razão como fonte única.
 * {@code exercicio}/{@code mes} sem {@code periodo_contabil} cadastrado resolve
 * para uma matriz vazia (mesmo princípio "derivado, zero não é erro" de
 * {@link BalancetePort}).
 *
 * <p><b>FP (superávit financeiro) e DC (dívida consolidada) não aparecem neste
 * port.</b> O ADR-0057 as classifica como {@code Origem.DERIVADA}
 * ({@link br.contabil.razao.domain.InformacaoComplementar}) — "calculadas na
 * projeção a partir de saldos patrimoniais/DDR classes 7/8" — mas nenhum
 * ADR/doc/código deste repositório fixa a conta, o sinal ou a fórmula exata: FP
 * tem só uma pista indireta (a transposição de superávit por fonte na abertura da
 * DDR, docs/17 §96) e DC não tem nenhuma conta patrimonial candidata identificada.
 * Calcular aqui seria reconstruir por heurística sobre o razão — vedado pelo
 * ADR-0050 ("não é auditável, quebra a reconstrutibilidade, faz a MSC divergir do
 * razão como fonte única"). Fechar essa derivação é trabalho de modelagem
 * contábil novo (validação de fonte oficial MCASP/STN), não uma extensão mecânica
 * deste adapter — {@code [REVALIDAR]}, rastreado como gap explícito, não
 * contornado por suposição.
 */
public interface MatrizSaldosContabeisPort {

    List<LinhaMatrizSaldos> matriz(TenantId enteId, int exercicio, int mes);
}
