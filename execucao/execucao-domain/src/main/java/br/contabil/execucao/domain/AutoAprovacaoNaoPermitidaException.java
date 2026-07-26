package br.contabil.execucao.domain;

/**
 * Regra 9 / ADR-0023: veda auto-aprovação — o aprovador não pode ser o autor
 * do empenho nem da liquidação da mesma cadeia.
 */
public final class AutoAprovacaoNaoPermitidaException extends ExecucaoInvalidaException {

    public AutoAprovacaoNaoPermitidaException(String cadeia) {
        super(
                "auto_aprovacao_vedada",
                "aprovador não pode ser o autor do %s da mesma cadeia (Regra 9 — segregação de funções)"
                        .formatted(cadeia));
    }
}
