package br.contabil.execucao.domain.repository;

import java.time.LocalDate;
import java.util.Optional;

import br.contabil.execucao.domain.PaginaPagamentosRegistrados;
import br.contabil.plataforma.domain.TenantId;

/** Read model do registro de pagamentos por ente/período (RAZ-121) — mesmo padrão de {@link LiquidacoesRegistradasQuery}. */
public interface PagamentosRegistradosQuery {

    PaginaPagamentosRegistrados consultar(
            TenantId enteId, Optional<LocalDate> dataInicio, Optional<LocalDate> dataFim, int limite, Optional<String> cursor);
}
