package br.contabil.plataforma.infra.transparencia;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.transparencia.FiltroTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.PaginaTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.PublicacaoTransparencia;
import br.contabil.plataforma.domain.transparencia.TotalizacaoTransparenciaPublica;
import br.contabil.plataforma.domain.transparencia.TransparenciaPublicaQuery;

/** Adapter Postgres do read model público append-only da transparência ativa. */
@Repository
public class PostgresTransparenciaPublicaQuery implements TransparenciaPublicaQuery {

    private static final String SQL_BASE =
            """
            select ente_id, tipo_evento, recurso, sequencia, publicado_em, publicar_ate, payload_json::text as payload_json
              from transparencia_publicacao
             where ente_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresTransparenciaPublicaQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public PaginaTransparenciaPublica consultar(TenantId enteId, FiltroTransparenciaPublica filtro) {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(filtro, "filtro");

        StringBuilder sql = new StringBuilder(SQL_BASE);
        List<Object> parametros = new ArrayList<>();
        parametros.add(enteId.valor());
        aplicarFiltros(sql, parametros, filtro);
        filtro.cursor().ifPresent(cursor -> {
            Cursor decodificado = Cursor.decodificar(cursor);
            sql.append(" and (publicado_em, sequencia) < (?, ?)");
            parametros.add(decodificado.publicadoEm());
            parametros.add(decodificado.sequencia());
        });

        sql.append(" order by ").append(expressaoOrdenacao(filtro)).append(" ");
        sql.append(filtro.direcao() == FiltroTransparenciaPublica.DirecaoOrdenacao.ASC ? "asc" : "desc");
        sql.append(", publicado_em desc, sequencia desc limit ?");
        parametros.add(filtro.limite() + 1);

        List<PublicacaoTransparencia> linhas =
                jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapear(rs), parametros.toArray());
        boolean haMais = linhas.size() > filtro.limite();
        List<PublicacaoTransparencia> itens = haMais ? linhas.subList(0, filtro.limite()) : linhas;

        Optional<String> proximoCursor = haMais
                ? Optional.of(Cursor.de(itens.get(itens.size() - 1)).codificar())
                : Optional.empty();
        return new PaginaTransparenciaPublica(
                itens,
                proximoCursor,
                haMais,
                contar(enteId, filtro),
                ultimaAtualizacao(enteId, filtro));
    }

    @Override
    public TotalizacaoTransparenciaPublica totalizar(TenantId enteId, FiltroTransparenciaPublica filtro) {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(filtro, "filtro");

        StringBuilder sql = new StringBuilder(
                """
                select coalesce(payload_json->>'estagio', 'desconhecido') as estagio,
                       coalesce(sum((payload_json->>'valor')::numeric), 0) as valor,
                       count(*) as quantidade
                  from transparencia_publicacao
                 where ente_id = ?
                """);
        List<Object> parametros = new ArrayList<>();
        parametros.add(enteId.valor());
        aplicarFiltros(sql, parametros, filtro);
        sql.append(" group by coalesce(payload_json->>'estagio', 'desconhecido') order by estagio");

        List<TotalizacaoTransparenciaPublica.Linha> linhas = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new TotalizacaoTransparenciaPublica.Linha(
                        rs.getString("estagio"), rs.getBigDecimal("valor"), rs.getLong("quantidade")),
                parametros.toArray());
        BigDecimal total = linhas.stream()
                .map(TotalizacaoTransparenciaPublica.Linha::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TotalizacaoTransparenciaPublica(linhas, total, contar(enteId, filtro), ultimaAtualizacao(enteId, filtro));
    }

    private long contar(TenantId enteId, FiltroTransparenciaPublica filtro) {
        StringBuilder sql = new StringBuilder("select count(*) from transparencia_publicacao where ente_id = ?");
        List<Object> parametros = new ArrayList<>();
        parametros.add(enteId.valor());
        aplicarFiltros(sql, parametros, filtro);
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, parametros.toArray());
        return total == null ? 0L : total;
    }

    private Optional<Instant> ultimaAtualizacao(TenantId enteId, FiltroTransparenciaPublica filtro) {
        StringBuilder sql = new StringBuilder("select max(publicado_em) from transparencia_publicacao where ente_id = ?");
        List<Object> parametros = new ArrayList<>();
        parametros.add(enteId.valor());
        aplicarFiltros(sql, parametros, filtro);
        Instant instant = jdbcTemplate.queryForObject(sql.toString(), Instant.class, parametros.toArray());
        return Optional.ofNullable(instant);
    }

    private static void aplicarFiltros(StringBuilder sql, List<Object> parametros, FiltroTransparenciaPublica filtro) {
        filtro.estagio().ifPresent(valor -> {
            sql.append(" and payload_json->>'estagio' = ?");
            parametros.add(valor);
        });
        filtro.credorId().ifPresent(valor -> {
            sql.append(" and payload_json->>'credorId' = ?");
            parametros.add(valor);
        });
        filtro.orgaoId().ifPresent(valor -> {
            sql.append(" and coalesce(payload_json->>'orgaoId', payload_json->>'unidadeGestoraId') = ?");
            parametros.add(valor);
        });
        filtro.dataInicio().ifPresent(valor -> {
            sql.append(" and coalesce(payload_json->>'dataFato', payload_json->>'dataCompetencia') >= ?");
            parametros.add(valor.toString());
        });
        filtro.dataFim().ifPresent(valor -> {
            sql.append(" and coalesce(payload_json->>'dataFato', payload_json->>'dataCompetencia') <= ?");
            parametros.add(valor.toString());
        });
        filtro.funcao().ifPresent(valor -> {
            sql.append(" and payload_json->>'funcao' = ?");
            parametros.add(valor);
        });
        filtro.numeroEmpenho().ifPresent(valor -> {
            sql.append(" and (payload_json->>'numeroSequencial')::bigint = ?");
            parametros.add(valor);
        });
        filtro.contratoId().ifPresent(valor -> {
            sql.append(" and payload_json->>'contratoId' = ?");
            parametros.add(valor);
        });
    }

    private static String expressaoOrdenacao(FiltroTransparenciaPublica filtro) {
        return switch (filtro.ordenarPor()) {
            case "data" -> "coalesce(payload_json->>'dataFato', payload_json->>'dataCompetencia')";
            case "valor" -> "(payload_json->>'valor')::numeric";
            case "numeroEmpenho" -> "(payload_json->>'numeroSequencial')::bigint";
            default -> "publicado_em";
        };
    }

    private static PublicacaoTransparencia mapear(ResultSet rs) throws SQLException {
        return new PublicacaoTransparencia(
                new TenantId(rs.getObject("ente_id", java.util.UUID.class)),
                rs.getString("tipo_evento"),
                rs.getString("recurso"),
                rs.getLong("sequencia"),
                rs.getTimestamp("publicado_em").toInstant(),
                rs.getTimestamp("publicar_ate").toInstant(),
                rs.getString("payload_json"));
    }

    /** Cursor keyset opaco: {@code publicado_em|sequencia}, Base64URL. */
    private record Cursor(Instant publicadoEm, long sequencia) {

        static Cursor de(PublicacaoTransparencia item) {
            return new Cursor(item.publicadoEm(), item.sequencia());
        }

        String codificar() {
            String bruto = publicadoEm + "|" + sequencia;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bruto.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decodificar(String opaco) {
            try {
                String bruto = new String(Base64.getUrlDecoder().decode(opaco), StandardCharsets.UTF_8);
                int separador = bruto.indexOf('|');
                if (separador < 0) {
                    throw new IllegalArgumentException("cursor sem separador");
                }
                return new Cursor(
                        Instant.parse(bruto.substring(0, separador)),
                        Long.parseLong(bruto.substring(separador + 1)));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("cursor inválido para transparência pública", ex);
            }
        }
    }
}
