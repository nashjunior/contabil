package br.contabil.execucao.domain;

/**
 * ADR-0023: {@code RegistrarPagamento} exige a liquidação em status {@code
 * aprovada} — pular o gate de {@code AprovarPagamento} (mesmo tendo {@code
 * Acao.CRIAR}) é recusado com este erro de contrato.
 */
public final class PagamentoNaoAprovadoException extends ExecucaoInvalidaException {

    public PagamentoNaoAprovadoException(LiquidacaoId liquidacaoId, StatusAprovacao statusAtual) {
        super(
                "pagamento_nao_aprovado",
                "liquidação %s não está aprovada (status atual: %s)"
                        .formatted(liquidacaoId.valor(), statusAtual));
    }
}
