package br.contabil.execucao.domain.repository;

import java.util.Optional;

import br.contabil.execucao.domain.PaginaDotacoes;
import br.contabil.plataforma.domain.TenantId;

/**
 * Read model da listagem de dotações por ente/exercício com saldo inline (RAZ-148).
 * Separado de {@link DotacaoRepository}, que é repositório de escrita/leitura pontual.
 */
public interface DotacoesQuery {

    PaginaDotacoes consultar(
            TenantId enteId, int exercicio, Optional<String> busca, int limite, Optional<String> cursor);
}
