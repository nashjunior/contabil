package br.contabil.execucao.infra;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.ItemDotacaoComSaldo;
import br.contabil.execucao.domain.PaginaDotacoes;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.DotacoesQuery;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/**
 * Adapter Postgres da listagem de dotações (RAZ-148). O saldo é derivado na
 * própria consulta: {@code valor_autorizado - sum(empenho.valor)}, mantendo o
 * mesmo conceito de {@link br.contabil.execucao.domain.SaldoDotacao}.
 */
@Repository
public class PostgresDotacoesQuery implements DotacoesQuery {

    private static final String SQL_BASE =
            """
            select d.id, d.exercicio, d.classificacao_orcamentaria, d.fonte_recurso,
                   d.unidade_gestora_id, d.valor_autorizado,
                   coalesce(sum(e.valor), 0) as valor_comprometido
              from dotacao d
              left join empenho e on e.ente_id = d.ente_id and e.dotacao_id = d.id
             where d.ente_id = ? and d.exercicio = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresDotacoesQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public PaginaDotacoes consultar(
            TenantId enteId, int exercicio, Optional<String> busca, int limite, Optional<String> cursor) {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(busca, "busca (Optional, nunca null)");
        Objects.requireNonNull(cursor, "cursor (Optional, nunca null)");

        StringBuilder sql = new StringBuilder(SQL_BASE);
        List<Object> parametros = new ArrayList<>();
        parametros.add(enteId.valor());
        parametros.add(exercicio);

        busca.ifPresent(valor -> {
            sql.append(" and d.classificacao_orcamentaria ilike ? escape '\\'");
            parametros.add(escaparLike(valor) + "%");
        });
        cursor.ifPresent(opaco -> {
            Cursor decodificado = Cursor.decodificar(opaco);
            sql.append(" and (d.classificacao_orcamentaria, d.id) > (?, ?)");
            parametros.add(decodificado.classificacaoOrcamentaria());
            parametros.add(decodificado.id());
        });

        sql.append(
                """
                 group by d.id, d.exercicio, d.classificacao_orcamentaria, d.fonte_recurso,
                          d.unidade_gestora_id, d.valor_autorizado
                 order by d.classificacao_orcamentaria asc, d.id asc limit ?
                """);
        parametros.add(limite + 1);

        List<ItemDotacaoComSaldo> linhas =
                jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapear(rs), parametros.toArray());

        boolean temProxima = linhas.size() > limite;
        List<ItemDotacaoComSaldo> itens = temProxima ? linhas.subList(0, limite) : linhas;
        Optional<String> proximoCursor = temProxima
                ? Optional.of(Cursor.de(itens.get(itens.size() - 1)).codificar())
                : Optional.empty();
        return new PaginaDotacoes(itens, proximoCursor);
    }

    private static ItemDotacaoComSaldo mapear(ResultSet rs) throws SQLException {
        return new ItemDotacaoComSaldo(
                new DotacaoId(rs.getObject("id", UUID.class)),
                rs.getInt("exercicio"),
                rs.getString("classificacao_orcamentaria"),
                rs.getString("fonte_recurso"),
                new UnidadeGestoraId(rs.getObject("unidade_gestora_id", UUID.class)),
                new Dinheiro(rs.getBigDecimal("valor_autorizado")),
                new Dinheiro(rs.getBigDecimal("valor_comprometido")));
    }

    private static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Cursor keyset opaco: {@code classificacao_orcamentaria|dotacaoId}, Base64URL. */
    private record Cursor(String classificacaoOrcamentaria, UUID id) {

        static Cursor de(ItemDotacaoComSaldo item) {
            return new Cursor(item.classificacaoOrcamentaria(), item.id().valor());
        }

        String codificar() {
            String bruto = classificacaoOrcamentaria + "|" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bruto.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decodificar(String opaco) {
            try {
                String bruto = new String(Base64.getUrlDecoder().decode(opaco), StandardCharsets.UTF_8);
                int separador = bruto.lastIndexOf('|');
                if (separador < 0) {
                    throw new IllegalArgumentException("cursor sem separador");
                }
                return new Cursor(
                        bruto.substring(0, separador),
                        UUID.fromString(bruto.substring(separador + 1)));
            } catch (IllegalArgumentException erro) {
                throw new ExecucaoInvalidaException("cursor_invalido", "cursor de paginação inválido");
            }
        }
    }
}
