package br.contabil.razao.domain;

import java.util.List;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Balancete de um período/ente: lista de contas com saldo anterior, movimento a
 * débito/crédito do período e saldo atual (razao-contabil-schema.md §Saldos).
 * Read-model DERIVADO dos lançamentos, nunca fonte da verdade (ADR-0007) — mesmo
 * princípio de {@link br.contabil.razao.domain.repository.ConsultaSaldoPort}.
 *
 * <p>{@link #confere()} materializa a invariante "Balancete de encerramento não
 * fecha (D≠C) → Encerramento bloqueado" (arquitetura-tecnica/README.md §6): como
 * cada fato já é Σdébito=Σcrédito (trava 1 do razão), a soma dos movimentos de
 * QUALQUER período fechado de fatos também deve bater — {@code confere() == false}
 * sinaliza uma inconsistência (dado espúrio/migração malformada, gap de
 * conta_pcasp.natureza_saldo), não uma condição esperada em operação normal.
 */
public record Balancete(TenantId enteId, int exercicio, int mes, List<LinhaBalancete> linhas) {

    public Balancete {
        Validacoes.exigirNaoNulo(enteId, "enteId");
        Validacoes.exigirNaoNulo(linhas, "linhas");
        linhas = List.copyOf(linhas);
    }

    public Dinheiro totalMovimentoDebito() {
        return linhas.stream().map(LinhaBalancete::movimentoDebito).reduce(Dinheiro.zero(), Dinheiro::somar);
    }

    public Dinheiro totalMovimentoCredito() {
        return linhas.stream().map(LinhaBalancete::movimentoCredito).reduce(Dinheiro.zero(), Dinheiro::somar);
    }

    /** Σ movimento a débito = Σ movimento a crédito do período — balancete "fecha". */
    public boolean confere() {
        return totalMovimentoDebito().equals(totalMovimentoCredito());
    }
}
