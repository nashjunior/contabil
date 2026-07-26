package br.contabil.razao.domain;

import java.util.Objects;
import java.util.UUID;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Uma linha do {@link Balancete}: saldo anterior/movimento a débito/movimento a
 * crédito/saldo atual de uma conta do PCASP no período. Saldos são o valor bruto
 * ΣD-ΣC (mesma convenção de {@code saldo_conta}, razao-contabil-schema.md) — a
 * interpretação do sinal segue {@code conta_pcasp.natureza_saldo}, não é
 * recalculada aqui.
 */
public record LinhaBalancete(
        UUID contaId,
        String codigo,
        String descricao,
        Dinheiro saldoAnterior,
        Dinheiro movimentoDebito,
        Dinheiro movimentoCredito,
        Dinheiro saldoAtual) {

    public LinhaBalancete {
        Objects.requireNonNull(contaId, "contaId não pode ser nulo");
        Objects.requireNonNull(codigo, "codigo não pode ser nulo");
        Objects.requireNonNull(descricao, "descricao não pode ser nula");
        Objects.requireNonNull(saldoAnterior, "saldoAnterior não pode ser nulo");
        Objects.requireNonNull(movimentoDebito, "movimentoDebito não pode ser nulo");
        Objects.requireNonNull(movimentoCredito, "movimentoCredito não pode ser nulo");
        Objects.requireNonNull(saldoAtual, "saldoAtual não pode ser nulo");
    }
}
