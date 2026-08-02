package br.contabil.razao.domain;

import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Uma linha da {@code MatrizSaldosContabeisPort}: o {@link LinhaBalancete}
 * estendido pela dimensão das informações complementares (IC) da MSC —
 * conta PCASP último nível × IC × NATUREZA × VALOR (ADR-0056/ADR-0057).
 * {@code ic} carrega as IC de partida capturadas no lançamento (FR/CO/NR/ND/FS/AI,
 * nunca nula como um todo — {@link InformacoesComplementares#nenhuma()} quando a
 * partida não carrega nenhuma); {@code poderOrgao} é a IC fato-wide (PO), nula
 * quando o fato não a capturou. FP/DC ({@code Origem.DERIVADA}) não aparecem
 * aqui — ver o javadoc de {@code MatrizSaldosContabeisPort}.
 */
public record LinhaMatrizSaldos(
        ContaContabilId contaId,
        String codigo,
        String descricao,
        String naturezaSaldo,
        InformacoesComplementares ic,
        PoderOrgao poderOrgao,
        Dinheiro saldoAnterior,
        Dinheiro movimentoDebito,
        Dinheiro movimentoCredito,
        Dinheiro saldoAtual) {

    public LinhaMatrizSaldos {
        Objects.requireNonNull(contaId, "contaId não pode ser nulo");
        Objects.requireNonNull(codigo, "codigo não pode ser nulo");
        Objects.requireNonNull(descricao, "descricao não pode ser nula");
        Objects.requireNonNull(naturezaSaldo, "naturezaSaldo não pode ser nulo");
        if (!"D".equals(naturezaSaldo) && !"C".equals(naturezaSaldo)) {
            throw new IllegalArgumentException(
                    "naturezaSaldo deve ser 'D' (devedora) ou 'C' (credora): " + naturezaSaldo);
        }
        ic = ic == null ? InformacoesComplementares.nenhuma() : ic;
        // poderOrgao pode ser nulo: IC fato-wide ausente quando o fato não a capturou.
        Objects.requireNonNull(saldoAnterior, "saldoAnterior não pode ser nulo");
        Objects.requireNonNull(movimentoDebito, "movimentoDebito não pode ser nulo");
        Objects.requireNonNull(movimentoCredito, "movimentoCredito não pode ser nulo");
        Objects.requireNonNull(saldoAtual, "saldoAtual não pode ser nulo");
    }
}
