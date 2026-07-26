package br.contabil.execucao.domain.repository;

import java.time.LocalDate;
import java.util.Optional;

import br.contabil.execucao.domain.PaginaLiquidacoesRegistradas;
import br.contabil.plataforma.domain.TenantId;

/**
 * Read model do registro de liquidações por ente/período (RAZ-121). Distinto de
 * {@link FilaAprovacaoQuery}: aquele serve o gate de aprovação (Regra 9 imposta
 * no SQL); este é o registro completo, sem recorte de segregação de funções —
 * não decide nada, só mostra o que foi lançado.
 */
public interface LiquidacoesRegistradasQuery {

    PaginaLiquidacoesRegistradas consultar(
            TenantId enteId, Optional<LocalDate> dataInicio, Optional<LocalDate> dataFim, int limite, Optional<String> cursor);
}
