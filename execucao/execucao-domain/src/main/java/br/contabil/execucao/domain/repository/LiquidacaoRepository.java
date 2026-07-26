package br.contabil.execucao.domain.repository;

import br.contabil.execucao.domain.Liquidacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.plataforma.domain.TenantId;
import java.util.Optional;

/** Port de persistência de liquidações. */
public interface LiquidacaoRepository {

    void inserir(Liquidacao liquidacao);

    Optional<Liquidacao> buscarPorId(TenantId enteId, LiquidacaoId id);
}
