package br.contabil.razao.domain;

import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Uma linha CAPTURADA da {@link MatrizSaldosContabeis}: saldo anterior/movimento a
 * débito/movimento a crédito/saldo atual de uma conta PCASP último nível, agregado
 * também pelas informações complementares (IC) de origem {@code LANCAMENTO}
 * ({@code FR/CO/NR/ND/FS/AI}, via {@link #informacoesComplementares()}) e {@code FATO}
 * ({@code PO}, via {@link #poderOrgao()}) — extensão de {@link LinhaBalancete} pela
 * dimensão IC (ADR-0056/ADR-0057). As IC {@code DERIVADA} ({@code FP/DC}) não aparecem
 * aqui — ver {@link LinhaMatrizSaldosContabeisDerivada}.
 *
 * <p>Mesma convenção de sinal de {@link LinhaBalancete}: saldos são o valor bruto
 * ΣD-ΣC; a interpretação segue {@code naturezaSaldo} (a natureza natural da conta),
 * não é recalculada a partir do sinal do saldo.
 */
public record LinhaMatrizSaldosContabeis(
        ContaContabilId contaId,
        String codigo,
        String descricao,
        String naturezaSaldo,
        PoderOrgao poderOrgao,
        InformacoesComplementares informacoesComplementares,
        Dinheiro saldoAnterior,
        Dinheiro movimentoDebito,
        Dinheiro movimentoCredito,
        Dinheiro saldoAtual) {

    public LinhaMatrizSaldosContabeis {
        Objects.requireNonNull(contaId, "contaId não pode ser nulo");
        Objects.requireNonNull(codigo, "codigo não pode ser nulo");
        Objects.requireNonNull(descricao, "descricao não pode ser nula");
        Objects.requireNonNull(naturezaSaldo, "naturezaSaldo não pode ser nulo");
        if (!"D".equals(naturezaSaldo) && !"C".equals(naturezaSaldo)) {
            throw new IllegalArgumentException(
                    "naturezaSaldo deve ser 'D' (devedora) ou 'C' (credora): " + naturezaSaldo);
        }
        informacoesComplementares =
                informacoesComplementares == null ? InformacoesComplementares.nenhuma() : informacoesComplementares;
        Objects.requireNonNull(saldoAnterior, "saldoAnterior não pode ser nulo");
        Objects.requireNonNull(movimentoDebito, "movimentoDebito não pode ser nulo");
        Objects.requireNonNull(movimentoCredito, "movimentoCredito não pode ser nulo");
        Objects.requireNonNull(saldoAtual, "saldoAtual não pode ser nulo");
    }
}
