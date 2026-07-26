package br.contabil.execucao.domain.repository;

import br.contabil.execucao.domain.Empenho;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.plataforma.domain.TenantId;
import java.util.Optional;

/** Port de persistência de empenhos. */
public interface EmpenhoRepository {

    void inserir(Empenho empenho);

    Optional<Empenho> buscarPorId(TenantId enteId, EmpenhoId id);
}
