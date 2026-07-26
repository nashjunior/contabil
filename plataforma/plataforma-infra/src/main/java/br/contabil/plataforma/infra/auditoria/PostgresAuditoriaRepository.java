package br.contabil.plataforma.infra.auditoria;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.AuditoriaLeitura;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.domain.consulta.ConsultaPaginada;
import br.contabil.plataforma.domain.consulta.Direcao;
import br.contabil.plataforma.domain.consulta.Ordenacao;
import br.contabil.plataforma.domain.consulta.Paginacao;

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
    public Paginacao<EventoAuditoria> consultar(TenantId ente, ConsultaPaginada<FiltroAuditoria> consulta) {
        Objects.requireNonNull(ente, "ente");
        Objects.requireNonNull(consulta, "consulta");
        FiltroAuditoria filtro = consulta.filtro();

        StringBuilder where = new StringBuilder(" where ente_id = ? and momento >= ? and momento < ?");
        List<Object> parametros = new ArrayList<>();
        parametros.add(ente.valor());
        parametros.add(Timestamp.from(filtro.desde()));
        parametros.add(Timestamp.from(filtro.ate()));

        filtro.tipo().ifPresent(tipo -> {
            where.append(" and tipo = ?");
            parametros.add(tipo);
        });
        filtro.ator().ifPresent(ator -> {
            where.append(" and ator = ?");
            parametros.add(ator);
        });
        filtro.recurso().ifPresent(recurso -> {
            where.append(" and recurso = ?");
            parametros.add(recurso);
        });

        Long total = jdbcTemplate.queryForObject(
                "select count(*) from auditoria_evento" + where,
                Long.class,
                parametros.toArray());

        List<Object> parametrosPagina = new ArrayList<>(parametros);
        parametrosPagina.add(consulta.porPagina());
        parametrosPagina.add((consulta.pagina() - 1L) * consulta.porPagina());

        String sql =
                """
                select ente_id, tipo, ator, recurso, momento, detalhes::text as detalhes
                  from auditoria_evento
                """
                        + where
                        + ordemSql(consulta.ordenacoes())
                        + " limit ? offset ?";
        List<EventoAuditoria> itens =
                jdbcTemplate.query(sql, (rs, rowNum) -> mapearEvento(rs), parametrosPagina.toArray());

        return new Paginacao<>(consulta.pagina(), consulta.porPagina(), total == null ? 0 : total, itens);
    }

    private String ordemSql(List<Ordenacao> ordenacoes) {
        if (ordenacoes.isEmpty()) {
            return " order by momento, sequencia";
        }
        return ordenacoes.stream()
                .map(this::ordenacaoSql)
                .reduce(" order by ", (acumulado, ordem) -> acumulado.equals(" order by ")
                        ? acumulado + ordem
                        : acumulado + ", " + ordem);
    }

    private String ordenacaoSql(Ordenacao ordenacao) {
        String campo = switch (ordenacao.campo()) {
            case "ente" -> "ente_id";
            case "tipo" -> "tipo";
            case "ator" -> "ator";
            case "recurso" -> "recurso";
            case "momento" -> "momento";
            case "sequencia" -> "sequencia";
            default -> throw new IllegalArgumentException("campo de ordenação de auditoria não suportado: "
                    + ordenacao.campo());
        };
        return campo + (ordenacao.direcao() == Direcao.ASC ? " asc" : " desc");
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
