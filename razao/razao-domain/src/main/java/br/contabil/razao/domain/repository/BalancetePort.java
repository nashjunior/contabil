package br.contabil.razao.domain.repository;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.Balancete;

/**
 * Consulta do balancete (saldo anterior/movimento D/movimento C/saldo atual por
 * conta) de um período contábil — read-model DERIVADO dos lançamentos (ADR-0007),
 * mesmo princípio de {@link ConsultaSaldoPort}. Um período sem nenhum
 * lançamento devolve um {@link Balancete} de linhas vazias (não é erro); só
 * {@code exercicio}/{@code mes} sem {@code periodo_contabil} cadastrado é que
 * não tem contas para agregar.
 */
public interface BalancetePort {

    Balancete balancete(TenantId enteId, int exercicio, int mes);
}
