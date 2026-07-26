package br.contabil.razao.domain;

import java.util.Objects;

/**
 * Item do catálogo de contas do PCASP (RAZ-117 / ADR-0030 §6): projeção de leitura
 * derivada de {@code conta_pcasp} (ADR-0007 — read model, nunca fonte da verdade),
 * a mesma tabela que {@code ExecucaoContabilPortAdapter.resolverConta} usa
 * internamente. Serve à busca/descoberta do {@code contaId} que
 * {@code /razao/saldo} exige e que o operador não tem como adivinhar.
 *
 * <p>{@code naturezaSaldo} é o {@code char} D/C natural da conta (mesma convenção
 * de {@link LinhaBalancete}); {@code contaPaiId} é <b>nulo</b> na conta raiz
 * (a hierarquia do plano de contas não tem pai acima do topo).
 */
public record ContaResumo(
        ContaContabilId id,
        String codigo,
        String descricao,
        String naturezaSaldo,
        String naturezaInformacao,
        boolean escrituravel,
        ContaContabilId contaPaiId) {

    public ContaResumo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(descricao, "descricao");
        Objects.requireNonNull(naturezaSaldo, "naturezaSaldo");
        Objects.requireNonNull(naturezaInformacao, "naturezaInformacao");
        // contaPaiId pode ser nulo: conta raiz do plano não tem pai.
    }
}
