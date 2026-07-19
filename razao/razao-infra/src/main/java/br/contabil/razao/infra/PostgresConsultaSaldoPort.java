package br.contabil.razao.infra;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lê o saldo da view {@code saldo_conta} (razao-contabil-schema.md) — read-model
 * DERIVADO dos lançamentos, nunca fonte da verdade (ADR-0007). Conta sem
 * nenhum lançamento tem saldo zero (não é erro).
 */
@Component
public class PostgresConsultaSaldoPort implements ConsultaSaldoPort {

    private static final String SQL_SALDO =
            "select saldo_devedor_liquido from saldo_conta where ente_id = ? and conta_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public PostgresConsultaSaldoPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Dinheiro saldoDevedorLiquido(TenantId enteId, UUID contaId) {
        BigDecimal saldo = jdbcTemplate.query(
                SQL_SALDO,
                rs -> rs.next() ? rs.getBigDecimal("saldo_devedor_liquido") : null,
                enteId.valor(),
                contaId);
        return saldo == null ? Dinheiro.zero() : new Dinheiro(saldo);
    }
}
