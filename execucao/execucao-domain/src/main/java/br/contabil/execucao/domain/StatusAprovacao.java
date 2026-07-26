package br.contabil.execucao.domain;

/**
 * Estado forte da aprovação da liquidação (ADR-0023): transicionado só pela
 * ação de domínio {@code Liquidacao#aprovar}/{@code Liquidacao#devolver} —
 * nunca derivado de saldo, ao contrário de {@code registrada}/{@code
 * paga_parcial}/{@code paga_total} (esses ficam no read model, fora do
 * agregado).
 */
public enum StatusAprovacao {
    PENDENTE,
    APROVADA,
    DEVOLVIDA
}
