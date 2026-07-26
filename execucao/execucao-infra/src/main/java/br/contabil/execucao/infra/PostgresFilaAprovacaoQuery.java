package br.contabil.execucao.infra;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.execucao.domain.CredorId;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.FiltroFilaAprovacao;
import br.contabil.execucao.domain.ItemFilaAprovacao;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.PaginaFilaAprovacao;
import br.contabil.execucao.domain.StatusAprovacao;
import br.contabil.execucao.domain.repository.FilaAprovacaoQuery;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;

/**
 * Adapter Postgres do read model da fila de aprovação (ADR-0029 §1/§2; JdbcTemplate,
 * mesma escolha do resto de {@code execucao-infra}).
 *
 * <p>A segregação da Regra 9 é imposta <b>no SQL</b>: linhas cujo autor (da liquidação
 * <b>ou</b> do empenho) seja o solicitante são excluídas por {@code autor_cpf IS DISTINCT
 * FROM ?} — nunca escondidas só na UI. {@code IS DISTINCT FROM} (não {@code <>}) mantém
 * linhas legadas de autor nulo visíveis (autor desconhecido ≠ solicitante) sem que um NULL
 * derrube o predicado.
 *
 * <p>Paginação por keyset {@code (data_competencia, id)} ascendente (fila = mais antigos
 * primeiro), cursor opaco Base64URL de {@code data|uuid}. Busca {@code limite + 1} para
 * saber se há próxima página sem um {@code COUNT(*)} da fila inteira (ADR-0007).
 */
@Repository
public class PostgresFilaAprovacaoQuery implements FilaAprovacaoQuery {

    private static final String SQL_BASE =
            """
            select l.id, l.empenho_id, l.data_competencia, l.valor, l.status_aprovacao,
                   e.numero_sequencial, e.exercicio, e.credor_id
              from liquidacao l
              join empenho e on e.ente_id = l.ente_id and e.id = l.empenho_id
             where l.ente_id = ?
               and l.status_aprovacao = ?
               and l.autor_cpf is distinct from ?
               and e.autor_cpf is distinct from ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresFilaAprovacaoQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public PaginaFilaAprovacao consultar(
            TenantId enteId, Cpf solicitante, FiltroFilaAprovacao filtro, int limite, Optional<String> cursor) {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(solicitante, "solicitante");
        Objects.requireNonNull(filtro, "filtro");
        Objects.requireNonNull(cursor, "cursor (Optional, nunca null)");

        StringBuilder sql = new StringBuilder(SQL_BASE);
        List<Object> parametros = new ArrayList<>();
        parametros.add(enteId.valor());
        parametros.add(filtro.statusAprovacao().name().toLowerCase());
        parametros.add(solicitante.numero());
        parametros.add(solicitante.numero());

        filtro.fonte().ifPresent(fonte -> {
            sql.append(" and e.fonte_recurso = ?");
            parametros.add(fonte);
        });
        filtro.dataInicio().ifPresent(dataInicio -> {
            sql.append(" and l.data_competencia >= ?");
            parametros.add(dataInicio);
        });
        filtro.dataFim().ifPresent(dataFim -> {
            sql.append(" and l.data_competencia <= ?");
            parametros.add(dataFim);
        });
        filtro.valorMin().ifPresent(valorMin -> {
            sql.append(" and l.valor >= ?");
            parametros.add(valorMin.valor());
        });
        filtro.valorMax().ifPresent(valorMax -> {
            sql.append(" and l.valor <= ?");
            parametros.add(valorMax.valor());
        });

        cursor.ifPresent(opaco -> {
            Cursor decodificado = Cursor.decodificar(opaco);
            sql.append(" and (l.data_competencia, l.id) > (?, ?)");
            parametros.add(decodificado.dataCompetencia());
            parametros.add(decodificado.id());
        });

        sql.append(" order by l.data_competencia asc, l.id asc limit ?");
        parametros.add(limite + 1);

        List<ItemFilaAprovacao> linhas =
                jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapear(rs), parametros.toArray());

        boolean temProxima = linhas.size() > limite;
        List<ItemFilaAprovacao> itens = temProxima ? linhas.subList(0, limite) : linhas;
        Optional<String> proximoCursor = temProxima
                ? Optional.of(Cursor.de(itens.get(itens.size() - 1)).codificar())
                : Optional.empty();
        return new PaginaFilaAprovacao(itens, proximoCursor);
    }

    private static ItemFilaAprovacao mapear(ResultSet rs) throws SQLException {
        return new ItemFilaAprovacao(
                new LiquidacaoId(rs.getObject("id", UUID.class)),
                new EmpenhoId(rs.getObject("empenho_id", UUID.class)),
                rs.getLong("numero_sequencial"),
                rs.getInt("exercicio"),
                new CredorId(rs.getObject("credor_id", UUID.class)),
                new Dinheiro(rs.getBigDecimal("valor")),
                rs.getDate("data_competencia").toLocalDate(),
                StatusAprovacao.valueOf(rs.getString("status_aprovacao").toUpperCase()));
    }

    /** Cursor keyset opaco: {@code data_competencia|liquidacaoId}, Base64URL. */
    private record Cursor(LocalDate dataCompetencia, UUID id) {

        static Cursor de(ItemFilaAprovacao item) {
            return new Cursor(item.dataCompetencia(), item.id().valor());
        }

        String codificar() {
            String bruto = dataCompetencia + "|" + id;
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
                        LocalDate.parse(bruto.substring(0, separador)),
                        UUID.fromString(bruto.substring(separador + 1)));
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException erro) {
                throw new ExecucaoInvalidaException("cursor_invalido", "cursor de paginação inválido");
            }
        }
    }
}
