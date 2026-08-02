package br.contabil.razao.application;

import java.util.Objects;

import br.contabil.razao.domain.ContaContabilId;

/**
 * Parâmetro de transposição de saldo na abertura do exercício seguinte (RAZ-259,
 * docs/15-fechamento-contabil.md §Preciso item 5 — IPC 03/STN rev. 2017, item 30/p.11):
 * a conta de origem, cujo saldo acumulado no exercício encerrado é zerado por um
 * lançamento de abertura, e a conta de destino que passa a carregar esse saldo no
 * exercício seguinte.
 *
 * <p>A natureza do lançamento em cada conta não é fixa aqui — {@link TransporSaldosAbertura}
 * a deriva do sinal do saldo devedor líquido apurado, preservando o sentido original
 * (superávit credor / déficit devedor) na conta de destino.
 *
 * <p>Os códigos PCASP concretos (ex.: {@code 2.3.7.1.1.01.00} — Superávits ou Déficits
 * do Exercício / {@code 2.3.7.1.1.02.00} — Superávits ou Déficits de Exercícios Anteriores)
 * são injetados pelo bootstrap — o use case é PCASP-agnóstico.
 *
 * @param contaOrigem  conta cujo saldo acumulado é transposto (zerada pela abertura)
 * @param contaDestino conta que passa a carregar o saldo no exercício seguinte
 */
public record ParametroTransposicaoAbertura(ContaContabilId contaOrigem, ContaContabilId contaDestino) {

    public ParametroTransposicaoAbertura {
        Objects.requireNonNull(contaOrigem, "contaOrigem");
        Objects.requireNonNull(contaDestino, "contaDestino");
    }
}
