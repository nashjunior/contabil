package br.contabil.razao.infra;

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
import org.springframework.stereotype.Component;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.ContaResumo;
import br.contabil.razao.domain.PaginaContas;
import br.contabil.razao.domain.repository.CatalogoContasPort;

/**
 * Adapter Postgres do catálogo de contas do PCASP (RAZ-117 / ADR-0030 §6) — lê a MESMA
 * {@code conta_pcasp} que {@code ExecucaoContabilPortAdapter.resolverConta} usa
 * internamente (read model derivado, ADR-0007). {@code JdbcTemplate}, como o resto de
 * {@code razao-infra}; tenant-scoped por RLS ({@code app.ente_id}) — o {@code ente_id = ?}
 * explícito é defesa em profundidade, não a única barreira.
 *
 * <p>Paginação por keyset {@code (codigo, id)} ascendente (mesma ordenação estável de
 * {@code PostgresBalancetePort}), cursor opaco Base64URL de {@code codigo|uuid}. Busca
 * {@code limite + 1} para saber se há próxima página sem um {@code COUNT(*)} do plano
 * inteiro (ADR-0007), espelhando {@code PostgresFilaAprovacaoQuery}. {@code busca} casa
 * <b>prefixo</b> de {@code codigo} ({@code like 'busca%'}) OU trecho de {@code descricao}
 * ({@code ilike '%busca%'}); os curingas do termo são escapados para tratar o {@code busca}
 * como texto literal, não como padrão SQL.
 */
@Component
public class PostgresCatalogoContasPort implements CatalogoContasPort {

    private static final String SQL_BASE =
            """
            select id, codigo, descricao, natureza_saldo, natureza_informacao, escrituravel, conta_pai_id
              from conta_pcasp
             where ente_id = ?
            """;

    private static final String SQL_EXISTE = "select 1 from conta_pcasp where ente_id = ? and id = ? limit 1";

    private final JdbcTemplate jdbcTemplate;

    public PostgresCatalogoContasPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public PaginaContas buscar(TenantId enteId, Optional<String> busca, int limite, Optional<String> cursor) {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(busca, "busca (Optional, nunca null)");
        Objects.requireNonNull(cursor, "cursor (Optional, nunca null)");

        StringBuilder sql = new StringBuilder(SQL_BASE);
        List<Object> parametros = new ArrayList<>();
        parametros.add(enteId.valor());

        busca.filter(termo -> !termo.isBlank()).ifPresent(termo -> {
            String literal = escaparLike(termo);
            sql.append(" and (codigo like ? escape '\\' or descricao ilike ? escape '\\')");
            parametros.add(literal + "%");
            parametros.add("%" + literal + "%");
        });

        cursor.ifPresent(opaco -> {
            Cursor decodificado = Cursor.decodificar(opaco);
            sql.append(" and (codigo, id) > (?, ?)");
            parametros.add(decodificado.codigo());
            parametros.add(decodificado.id());
        });

        sql.append(" order by codigo asc, id asc limit ?");
        parametros.add(limite + 1);

        List<ContaResumo> linhas =
                jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapear(rs), parametros.toArray());

        boolean temProxima = linhas.size() > limite;
        List<ContaResumo> itens = temProxima ? linhas.subList(0, limite) : linhas;
        Optional<String> proximoCursor =
                temProxima ? Optional.of(Cursor.de(itens.get(itens.size() - 1)).codificar()) : Optional.empty();
        return new PaginaContas(itens, proximoCursor);
    }

    @Override
    public boolean existe(TenantId enteId, ContaContabilId contaId) {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(contaId, "contaId");
        return !jdbcTemplate.query(SQL_EXISTE, (rs, rowNum) -> 1, enteId.valor(), contaId.valor()).isEmpty();
    }

    private static ContaResumo mapear(ResultSet rs) throws SQLException {
        UUID contaPai = rs.getObject("conta_pai_id", UUID.class);
        return new ContaResumo(
                new ContaContabilId(rs.getObject("id", UUID.class)),
                rs.getString("codigo"),
                rs.getString("descricao"),
                rs.getString("natureza_saldo"),
                rs.getString("natureza_informacao"),
                rs.getBoolean("escrituravel"),
                contaPai == null ? null : new ContaContabilId(contaPai));
    }

    /** Neutraliza os curingas de {@code LIKE}/{@code ILIKE} ({@code \ % _}) — {@code busca} é texto, não padrão. */
    private static String escaparLike(String termo) {
        return termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Cursor keyset opaco: {@code codigo|contaId}, Base64URL. Espelha o cursor de {@code PostgresFilaAprovacaoQuery}. */
    private record Cursor(String codigo, UUID id) {

        static Cursor de(ContaResumo item) {
            return new Cursor(item.codigo(), item.id().valor());
        }

        String codificar() {
            String bruto = codigo + "|" + id;
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
                        bruto.substring(0, separador), UUID.fromString(bruto.substring(separador + 1)));
            } catch (IllegalArgumentException erro) {
                throw new IllegalArgumentException("cursor de paginação inválido", erro);
            }
        }
    }
}
