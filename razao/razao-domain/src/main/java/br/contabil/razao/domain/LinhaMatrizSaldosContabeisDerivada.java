package br.contabil.razao.domain;

import java.util.Objects;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Uma linha DERIVADA da {@link MatrizSaldosContabeis}: {@code FP} (superávit
 * financeiro) ou {@code DC} (dívida consolidada) — informações complementares de
 * origem {@link InformacaoComplementar.Origem#DERIVADA} (ADR-0057), calculadas a
 * partir de saldos patrimoniais/DDR por {@link FonteRecurso}, nunca capturadas por
 * partida e sem coluna própria no razão ("saldo é derivado, nunca gravado", ADR-0007).
 *
 * <p>Rótulo sobre um saldo que já existe nas linhas capturadas da DDR (classes 7/8) —
 * não é fato novo. Por isso {@link MatrizSaldosContabeis#confere()} soma só as linhas
 * capturadas: somar esta linha de novo duplicaria o movimento já contado na conta PCASP
 * de origem.
 */
public record LinhaMatrizSaldosContabeisDerivada(
        InformacaoComplementar informacaoComplementar,
        FonteRecurso fonteRecurso,
        Dinheiro saldoAnterior,
        Dinheiro movimentoDebito,
        Dinheiro movimentoCredito,
        Dinheiro saldoAtual) {

    public LinhaMatrizSaldosContabeisDerivada {
        Objects.requireNonNull(informacaoComplementar, "informacaoComplementar não pode ser nula");
        if (informacaoComplementar.origem() != InformacaoComplementar.Origem.DERIVADA) {
            throw new IllegalArgumentException(
                    "linha derivada exige uma IC de origem DERIVADA (FP/DC): " + informacaoComplementar);
        }
        Objects.requireNonNull(fonteRecurso, "fonteRecurso não pode ser nula");
        Objects.requireNonNull(saldoAnterior, "saldoAnterior não pode ser nulo");
        Objects.requireNonNull(movimentoDebito, "movimentoDebito não pode ser nulo");
        Objects.requireNonNull(movimentoCredito, "movimentoCredito não pode ser nulo");
        Objects.requireNonNull(saldoAtual, "saldoAtual não pode ser nulo");
    }
}
