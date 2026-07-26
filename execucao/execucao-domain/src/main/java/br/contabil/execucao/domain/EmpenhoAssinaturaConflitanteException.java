package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.ErroContrato;

/**
 * Erro {@code empenho_assinatura_conflitante}: a transição condicional
 * {@code PENDENTE_ASSINATURA|ASSINATURA_REJEITADA -> ASSINADO} não encontrou o
 * empenho no estado esperado (ADR-0027 R3 — update condicional / lock
 * otimista). Duplo-clique ou retry pós-timeout gov.br caem aqui: a 2ª chamada é
 * no-op para o razão, nunca uma segunda assinatura.
 */
public final class EmpenhoAssinaturaConflitanteException extends RuntimeException implements ErroContrato {

    public EmpenhoAssinaturaConflitanteException(String mensagem) {
        super(mensagem);
    }

    @Override
    public String codigo() {
        return "empenho_assinatura_conflitante";
    }
}
