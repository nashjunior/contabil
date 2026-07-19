package br.contabil.plataforma.infra.auditoria;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.AuditoriaLeitura;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter Postgres da trilha de auditoria F0 (ADR-0005).
 *
 * <p>A escrita passa exclusivamente por {@code append_auditoria_evento(...)}:
 * a funcao deriva o ente de {@code app.ente_id}, usa timestamp do servidor,
 * calcula o SHA-256 no banco e atualiza o contador/hash anterior na mesma
 * transacao. A aplicacao nao tem {@code INSERT/UPDATE/DELETE} direto na tabela.
 */
@Repository
public class PostgresAuditoriaRepository implements AuditoriaEscrita, AuditoriaLeitura {

    private static final TypeReference<Map<String, String>> DETALHES_TYPE = new TypeReference<>() {};

    private static final String SQL_APPEND =
            """
            select id, sequencia, hash_evento, hash_anterior
              from append_auditoria_evento(?, ?, ?, ?, ?::jsonb)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public PostgresAuditoriaRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new ObjectMapper());
    }

    PostgresAuditoriaRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    @Transactional
    public EntradaTrilha append(EventoAuditoria evento) {
        Objects.requireNonNull(evento, "evento");
        try {
            String detalhesJson = objectMapper.writeValueAsString(evento.detalhes());
            return jdbcTemplate.queryForObject(
                    SQL_APPEND,
                    (rs, rowNum) -> new EntradaTrilha(
                            rs.getObject("id", UUID.class),
                            rs.getString("hash_evento"),
                            rs.getString("hash_anterior"),
                            rs.getLong("sequencia")),
                    evento.ente().valor(),
                    evento.tipo(),
                    evento.ator(),
                    evento.recurso(),
                    detalhesJson);
        } catch (DataAccessException | JsonProcessingException erro) {
            throw new FalhaAppendException("nao foi possivel anexar evento na trilha de auditoria", erro);
        }
    }

    @Override
    public List<EventoAuditoria> consultar(FiltroAuditoria filtro) {
        Objects.requireNonNull(filtro, "filtro");

        StringBuilder sql = new StringBuilder(
                """
                select ente_id, tipo, ator, recurso, momento, detalhes::text as detalhes
                  from auditoria_evento
                 where momento >= ? and momento < ?
                """);
        List<Object> parametros = new ArrayList<>();
        parametros.add(Timestamp.from(filtro.desde()));
        parametros.add(Timestamp.from(filtro.ate()));

        filtro.ente().ifPresent(ente -> {
            sql.append(" and ente_id = ?");
            parametros.add(ente.valor());
        });
        filtro.tipo().ifPresent(tipo -> {
            sql.append(" and tipo = ?");
            parametros.add(tipo);
        });
        filtro.ator().ifPresent(ator -> {
            sql.append(" and ator = ?");
            parametros.add(ator);
        });
        sql.append(" order by momento, sequencia");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapearEvento(rs), parametros.toArray());
    }

    private EventoAuditoria mapearEvento(ResultSet rs) throws SQLException {
        return new EventoAuditoria(
                new TenantId(rs.getObject("ente_id", UUID.class)),
                rs.getString("tipo"),
                rs.getString("ator"),
                rs.getString("recurso"),
                rs.getTimestamp("momento").toInstant(),
                lerDetalhes(rs.getString("detalhes")));
    }

    private Map<String, String> lerDetalhes(String json) {
        try {
            return objectMapper.readValue(json, DETALHES_TYPE);
        } catch (JsonProcessingException erro) {
            throw new FalhaAppendException("evento de auditoria persistido com detalhes invalidos", erro);
        }
    }

}
