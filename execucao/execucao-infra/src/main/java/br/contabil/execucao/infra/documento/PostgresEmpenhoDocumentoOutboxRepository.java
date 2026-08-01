package br.contabil.execucao.infra.documento;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.plataforma.domain.TenantId;

/**
 * SECURITY DEFINER por {@code id} (mesmo padrão de {@code
 * PostgresOutboxMensagemRepository}/V4): o worker roda sem {@code app.ente_id}
 * setado (fora de qualquer {@code executar(..)} advisado), então reclamar/
 * confirmar/retentar/DLQ precisam atravessar a RLS por uma superfície estreita
 * — nunca um SELECT cross-tenant genérico.
 */
@Repository
class PostgresEmpenhoDocumentoOutboxRepository implements EmpenhoDocumentoOutboxRepository {

    private static final String SQL_RECLAMAR = "select * from execucao_empenho_documento_outbox_reclamar(?, ?)";
    private static final String SQL_CONFIRMAR = "select execucao_empenho_documento_outbox_confirmar(?)";
    private static final String SQL_RETENTATIVA = "select execucao_empenho_documento_outbox_retentativa(?, ?, ?)";
    private static final String SQL_DLQ = "select execucao_empenho_documento_outbox_dlq(?, ?)";

    private final JdbcTemplate jdbcTemplate;

    PostgresEmpenhoDocumentoOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MensagemEmpenhoDocumentoOutbox> reclamar(int limite, Instant bloqueadoAte) {
        return jdbcTemplate.query(SQL_RECLAMAR, this::mapear, limite, Timestamp.from(bloqueadoAte));
    }

    @Override
    public void confirmar(UUID id) {
        jdbcTemplate.query(SQL_CONFIRMAR, rs -> {}, id);
    }

    @Override
    public void registrarRetentativa(UUID id, Instant proximaTentativa, String erro) {
        jdbcTemplate.query(SQL_RETENTATIVA, rs -> {}, id, Timestamp.from(proximaTentativa), erro);
    }

    @Override
    public void enviarParaDlq(UUID id, String erro) {
        jdbcTemplate.query(SQL_DLQ, rs -> {}, id, erro);
    }

    private MensagemEmpenhoDocumentoOutbox mapear(ResultSet rs, int rowNum) throws SQLException {
        return new MensagemEmpenhoDocumentoOutbox(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("ente_id", UUID.class)),
                new EmpenhoId(rs.getObject("empenho_id", UUID.class)),
                rs.getString("correlation_id"),
                rs.getTimestamp("criado_em").toInstant(),
                rs.getInt("tentativas"));
    }
}
