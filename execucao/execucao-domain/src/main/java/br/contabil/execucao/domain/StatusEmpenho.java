package br.contabil.execucao.domain;

/**
 * Estado forte do <b>documento</b> da nota de empenho (ADR-0027), ortogonal ao
 * comprometimento contábil — este último já é efetivo em {@link #REGISTRADO}
 * (Lei 4.320/1964 art. 58: o fato é escriturado e o saldo da dotação
 * comprometido em {@code RegistrarEmpenho}). {@code PENDENTE_ASSINATURA}/{@code
 * ASSINADO}/{@code ASSINATURA_REJEITADA} descrevem só o ciclo de vida da nota
 * (art. 61) — nunca gate a escrituração/saldo por este status (R1).
 *
 * <p>{@code ASSINATURA_REJEITADA} é retrabalho (o empenho segue comprometido,
 * aguarda nova tentativa de assinatura) — abandono definitivo é anulação de
 * empenho por estorno append-only, issue própria (R2).
 */
public enum StatusEmpenho {
    REGISTRADO,
    PENDENTE_ASSINATURA,
    ASSINADO,
    ASSINATURA_REJEITADA
}
