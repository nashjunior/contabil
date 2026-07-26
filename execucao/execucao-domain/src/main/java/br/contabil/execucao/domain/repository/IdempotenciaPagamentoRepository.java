package br.contabil.execucao.domain.repository;

import br.contabil.execucao.domain.PagamentoId;
import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.TenantId;

/**
 * Idempotência ponta a ponta do registro de pagamento (ADR-0011): a reserva
 * acontece na mesma transação do INSERT em {@code pagamento} — replay com a
 * mesma chave nunca duplica o efeito (fato contábil + linha de pagamento).
 */
public interface IdempotenciaPagamentoRepository {

    /**
     * Reserva {@code chave} para {@code candidato}. Se a chave é nova, a reserva
     * "vence" e o retorno é o próprio {@code candidato} — prossiga com o registro
     * normal. Se a chave já foi usada por um pagamento anterior, o retorno é o
     * {@link PagamentoId} original: não repita o efeito, devolva o resultado já
     * existente (replay idempotente).
     */
    PagamentoId reservar(TenantId enteId, ChaveIdempotencia chave, PagamentoId candidato);
}
