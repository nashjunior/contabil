package br.contabil.execucao.domain.repository;

import br.contabil.execucao.domain.Pagamento;
import br.contabil.execucao.domain.PagamentoId;
import br.contabil.plataforma.domain.TenantId;
import java.util.Optional;

/** Port de persistência de pagamentos. */
public interface PagamentoRepository {

    void inserir(Pagamento pagamento);

    Optional<Pagamento> buscarPorId(TenantId enteId, PagamentoId id);
}
