package br.contabil.razao.domain.repository;

import java.util.Optional;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.FatoContabilId;

/**
 * Port de persistência do fato contábil. Deliberadamente SEM {@code atualizar}/
 * {@code excluir} na assinatura — o razão é append-only (Regra 3/4); proibido no
 * nível do tipo, não só por convenção (guardiao-arquitetura, ArchUnit
 * {@code repositorio_do_razao_e_append_only}).
 *
 * <p>{@link #inserir(FatoContabil)} grava o fato e TODOS os seus lançamentos
 * numa única transação, tudo ou nada — não é fail-soft (ADR-0013 não se aplica
 * aqui; a unidade de atomicidade é o fato inteiro).
 */
public interface FatoContabilRepository {

    void inserir(FatoContabil fato);

    Optional<FatoContabil> buscarPorId(TenantId enteId, FatoContabilId id);
}
