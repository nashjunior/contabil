package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.Validacoes;

/**
 * Item de entrada de um crédito adicional (Lei 4.320/1964 arts. 40-46): soma
 * {@code valor} ao {@code valorAutorizado} de uma {@link Dotacao} já
 * existente. Só transporta dados — validação de negócio (valor positivo)
 * acontece no caso de uso, item a item, fail-soft (ADR-0013).
 */
public record CreditoAdicional(DotacaoId dotacaoId, TipoCreditoAdicional tipo, Dinheiro valor, String historico) {

    public CreditoAdicional {
        Validacoes.exigirNaoNulo(dotacaoId, "dotacaoId");
        Validacoes.exigirNaoNulo(tipo, "tipo");
        Validacoes.exigirNaoNulo(valor, "valor");
        Validacoes.exigirNaoNulo(historico, "histórico");
    }
}
