package br.contabil.execucao.domain;

/**
 * Dupla decisão sobre a mesma liquidação (ADR-0029 §4): o estado forte do
 * agregado já saiu de {@code PENDENTE} — outra decisão chegou primeiro. É
 * conflito de estado, não payload malformado, então mapeia para {@code 409}
 * (mesmo bucket de {@link SaldoInsuficienteException}/{@link
 * PagamentoNaoAprovadoException}/{@link AutoAprovacaoNaoPermitidaException}),
 * dando ao cliente de lote ({@code Promise.allSettled} client-side, ADR-0023)
 * o sinal para tratar "já decidida" sem falso erro de validação.
 */
public final class LiquidacaoJaDecididaException extends ExecucaoInvalidaException {

    public LiquidacaoJaDecididaException(LiquidacaoId id, StatusAprovacao statusAtual) {
        super(
                "liquidacao_ja_decidida",
                "liquidação %s já foi decidida (status atual: %s)".formatted(id.valor(), statusAtual));
    }
}
