package br.contabil.execucao.domain.repository;

import java.util.Optional;

import br.contabil.execucao.domain.Pagamento;
import br.contabil.execucao.domain.PagamentoId;
import br.contabil.plataforma.domain.TenantId;

/** Port de persistência de pagamentos. */
public interface PagamentoRepository {

    void inserir(Pagamento pagamento);

    Optional<Pagamento> buscarPorId(TenantId enteId, PagamentoId id);
}
