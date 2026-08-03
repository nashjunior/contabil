package br.contabil.razao.domain.repository;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.MatrizSaldosContabeis;

/**
 * Consulta da Matriz de Saldos Contábeis (MSC) de um período contábil — read-model
 * DERIVADO dos lançamentos (ADR-0007), no padrão {@link BalancetePort}, estendido pela
 * dimensão das informações complementares (IC, ADR-0056/ADR-0057): {@code GROUP BY}
 * conta PCASP último nível × IC, com {@code FP}/{@code DC} derivados de saldos DDR
 * classes 7/8 (RAZ-225/ADR-0054). Consumido por {@code prestacao-contas} (RAZ-251) para
 * o unpivot no long-format do SICONFI — este port devolve o agregado nativo do razão, não
 * o leiaute Anexo II/XBRL GL.
 *
 * <p>Um período sem nenhum lançamento devolve uma {@link MatrizSaldosContabeis} de linhas
 * vazias (não é erro); só {@code exercicio}/{@code mes} sem {@code periodo_contabil}
 * cadastrado é que não tem contas para agregar.
 */
public interface MatrizSaldosContabeisPort {

    MatrizSaldosContabeis matriz(TenantId enteId, int exercicio, int mes);
}
