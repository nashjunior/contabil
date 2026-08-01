package br.contabil.plataforma.infra.entrega;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;

@Repository
class PostgresOutboxMensagemRepository implements OutboxMensagemRepository {

    private static final String SQL_RECLAMAR = "select * from outbox_entrega_reclamar(?, ?)";
    private static final String SQL_CONFIRMAR = "select outbox_entrega_confirmar(?)";
    private static final String SQL_RETENTATIVA = "select outbox_entrega_retentativa(?, ?, ?)";
    private static final String SQL_DLQ = "select outbox_entrega_dlq(?, ?)";

    private final JdbcTemplate jdbcTemplate;

    PostgresOutboxMensagemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MensagemOutbox> reclamar(int limite, Instant bloqueadoAte) {
        return jdbcTemplate.query(SQL_RECLAMAR, this::mapear, limite, Timestamp.from(bloqueadoAte));
    }

    @Override
    public void confirmarEntrega(IdEntrega id) {
        jdbcTemplate.query(SQL_CONFIRMAR, rs -> {}, id.valor());
    }

    @Override
    public void registrarRetentativa(IdEntrega id, Instant proximaTentativa, String erro) {
        jdbcTemplate.query(SQL_RETENTATIVA, rs -> {}, id.valor(), Timestamp.from(proximaTentativa), erro);
    }

    @Override
    public void enviarParaDlq(IdEntrega id, String erro) {
        jdbcTemplate.query(SQL_DLQ, rs -> {}, id.valor(), erro);
    }

    private MensagemOutbox mapear(ResultSet rs, int rowNum) throws SQLException {
        return new MensagemOutbox(
                new IdEntrega(rs.getObject("id", UUID.class)),
                new TenantId(rs.getObject("ente_id", UUID.class)),
                ChaveIdempotencia.de(rs.getString("chave")),
                rs.getString("destino"),
                rs.getString("tipo"),
                rs.getString("conteudo"),
                rs.getString("correlation_id"),
                rs.getTimestamp("criado_em").toInstant(),
                rs.getInt("tentativas"));
    }
}
