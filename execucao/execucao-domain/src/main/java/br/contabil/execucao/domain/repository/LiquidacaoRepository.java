package br.contabil.execucao.domain.repository;

import java.util.Optional;

import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.plataforma.domain.TenantId;

/** Port de persistência de liquidações. */
public interface LiquidacaoRepository {

    void inserir(Liquidacao liquidacao);

    Optional<Liquidacao> buscarPorId(TenantId enteId, LiquidacaoId id);

    /**
     * Persiste a decisão de aprovação (ADR-0023: {@code aprovada}/{@code devolvida} é estado
     * forte, transicionado só por {@code AprovarPagamento}) — não é um update de negócio livre,
     * é a única mutação permitida sobre a liquidação já registrada.
     */
    void atualizarDecisaoAprovacao(Liquidacao liquidacao);
}
