package br.contabil.razao.domain;

import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Uma linha do {@link Balancete}: saldo anterior/movimento a débito/movimento a
 * crédito/saldo atual de uma conta do PCASP no período. Saldos são o valor bruto
 * ΣD-ΣC (mesma convenção de {@code saldo_conta}, razao-contabil-schema.md) — a
 * interpretação do sinal segue {@code naturezaSaldo} (a natureza <b>natural</b> da
 * conta, {@code conta_pcasp.natureza_saldo}: {@code "D"} devedora / {@code "C"}
 * credora), não é recalculada a partir do sinal do {@code saldoAtual}. Expor a
 * natureza deixa a UI sinalizar a anomalia "conta devedora com saldo credor"
 * (ADR-0030 §5) em vez de inferi-la.
 */
public record LinhaBalancete(
        ContaContabilId contaId,
        String codigo,
        String descricao,
        String naturezaSaldo,
        Dinheiro saldoAnterior,
        Dinheiro movimentoDebito,
        Dinheiro movimentoCredito,
        Dinheiro saldoAtual) {

    public LinhaBalancete {
        Objects.requireNonNull(contaId, "contaId não pode ser nulo");
        Objects.requireNonNull(codigo, "codigo não pode ser nulo");
        Objects.requireNonNull(descricao, "descricao não pode ser nula");
        Objects.requireNonNull(naturezaSaldo, "naturezaSaldo não pode ser nulo");
        if (!"D".equals(naturezaSaldo) && !"C".equals(naturezaSaldo)) {
            throw new IllegalArgumentException(
                    "naturezaSaldo deve ser 'D' (devedora) ou 'C' (credora): " + naturezaSaldo);
        }
        Objects.requireNonNull(saldoAnterior, "saldoAnterior não pode ser nulo");
        Objects.requireNonNull(movimentoDebito, "movimentoDebito não pode ser nulo");
        Objects.requireNonNull(movimentoCredito, "movimentoCredito não pode ser nulo");
        Objects.requireNonNull(saldoAtual, "saldoAtual não pode ser nulo");
    }
}
