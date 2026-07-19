package br.contabil.razao.domain.repository;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import java.util.UUID;

/**
 * Saldo como read-model DERIVADO dos lançamentos (ADR-0007) — nunca uma tabela
 * de saldo tratada como fonte da verdade. Consistência eventual aceitável
 * dentro do SLA do ADR-0007.
 */
public interface ConsultaSaldoPort {

    Dinheiro saldoDevedorLiquido(TenantId enteId, UUID contaId);
}
