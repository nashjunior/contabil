package br.contabil.consulta;

import java.math.BigDecimal;
import java.util.UUID;

/** Corpo HTTP de {@code GET .../razao/saldo} — RAZ-101. */
record SaldoContaResponse(UUID contaId, BigDecimal saldo) {}
