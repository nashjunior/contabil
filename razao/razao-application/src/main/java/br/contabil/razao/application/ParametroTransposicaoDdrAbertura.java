package br.contabil.razao.application;

import java.util.Objects;

import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.Natureza;

/**
 * Parâmetro de transposição da DDR (classes 7/8) por fonte na abertura do exercício
 * seguinte (RAZ-244, docs/15-fechamento-contabil.md §Preciso item 5, IPC 03/STN rev.
 * 2017 §96): a conta de origem (recursos disponíveis do exercício encerrado, por
 * fonte) e a conta de destino (recursos de exercícios anteriores) que passa a
 * carregar o superávit financeiro no exercício seguinte.
 *
 * <p>Diferente de {@link ParametroTransposicaoAbertura} (saldo patrimonial único,
 * natureza derivada do sinal — superávit credor/déficit devedor podem alternar), a
 * direção aqui é <b>fixa</b>: a disponibilidade de recursos é sempre saldo credor
 * (nunca se compromete além do disponível — trava art. 42/ADR-0044), por isso a
 * mesma direção documentada pela IPC 03 §96 (não deriva do sinal).
 *
 * <p>Os códigos PCASP concretos (ex.: {@code 8.2.1.1.1.01.00} — Recursos Disponíveis
 * para o Exercício / {@code 8.2.1.1.1.02.00} — Recursos de Exercícios Anteriores) são
 * injetados pelo bootstrap — {@code [REVALIDAR]} MCASP edição vigente antes de
 * configurar em produção.
 *
 * @param contaOrigem     conta cujos saldos por fonte são transpostos (zerada na abertura)
 * @param contaDestino    conta que passa a carregar o saldo por fonte no exercício seguinte
 * @param naturezaDestino natureza do lançamento na conta de destino (a origem recebe a inversa)
 */
public record ParametroTransposicaoDdrAbertura(
        ContaContabilId contaOrigem,
        ContaContabilId contaDestino,
        Natureza naturezaDestino) {

    public ParametroTransposicaoDdrAbertura {
        Objects.requireNonNull(contaOrigem, "contaOrigem");
        Objects.requireNonNull(contaDestino, "contaDestino");
        Objects.requireNonNull(naturezaDestino, "naturezaDestino");
    }
}
