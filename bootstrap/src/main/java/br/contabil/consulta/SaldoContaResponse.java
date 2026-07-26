package br.contabil.consulta;

import java.util.UUID;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Corpo HTTP de {@code GET .../razao/saldo} — RAZ-101. Carrega {@link Dinheiro}
 * (não {@code BigDecimal}): o {@link DinheiroJacksonModule} o serializa como
 * string decimal de 2 casas (RAZ-79 §6.1 / ADR-0030 §2).
 */
record SaldoContaResponse(UUID contaId, Dinheiro saldo) {}
