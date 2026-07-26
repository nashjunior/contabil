package br.contabil.execucao.domain.repository;

import br.contabil.execucao.domain.Dotacao;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.plataforma.domain.TenantId;
import java.util.Optional;

/** Port de persistência da dotação (Fixação/carga da LOA — RAZ-80). */
public interface DotacaoRepository {

    void inserir(Dotacao dotacao);

    Optional<Dotacao> buscarPorId(TenantId enteId, DotacaoId id);
}
