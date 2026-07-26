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

import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.ItemPagamentoRegistrado;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.NaturezaPagamento;
import br.contabil.execucao.domain.PagamentoId;
import br.contabil.execucao.domain.PaginaPagamentosRegistrados;
import br.contabil.execucao.domain.repository.PagamentosRegistradosQuery;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/**
 * Adapter Postgres do read model do registro de pagamentos (RAZ-121). Mesmo
 * padrão de {@link PostgresEmpenhosRegistradosQuery}/{@link
 * PostgresLiquidacoesRegistradasQuery}: keyset {@code (data_competencia, id)}
 * descendente, cursor opaco Base64URL. {@code beneficiario_nome}/{@code
 * beneficiario_cpf_cnpj} deliberadamente fora do {@code select} — {@link
 * ItemPagamentoRegistrado} não carrega PII (04-lgpd).
 */
@Repository
public class PostgresPagamentosRegistradosQuery implements PagamentosRegistradosQuery {

    private static final String SQL_BASE =
            """
            select id, liquidacao_id, valor, data_competencia, natureza, historico
              from pagamento
             where ente_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresPagamentosRegistradosQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public PaginaPagamentosRegistrados consultar(
            TenantId enteId, Optional<LocalDate> dataInicio, Optional<LocalDate> dataFim, int limite, Optional<String> cursor) {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(dataInicio, "dataInicio (Optional, nunca null)");
        Objects.requireNonNull(dataFim, "dataFim (Optional, nunca null)");
        Objects.requireNonNull(cursor, "cursor (Optional, nunca null)");

        StringBuilder sql = new StringBuilder(SQL_BASE);
        List<Object> parametros = new ArrayList<>();
        parametros.add(enteId.valor());

        dataInicio.ifPresent(valor -> {
            sql.append(" and data_competencia >= ?");
            parametros.add(valor);
        });
        dataFim.ifPresent(valor -> {
            sql.append(" and data_competencia <= ?");
            parametros.add(valor);
        });
        cursor.ifPresent(opaco -> {
            Cursor decodificado = Cursor.decodificar(opaco);
            sql.append(" and (data_competencia, id) < (?, ?)");
            parametros.add(decodificado.data());
            parametros.add(decodificado.id());
        });

        sql.append(" order by data_competencia desc, id desc limit ?");
        parametros.add(limite + 1);

        List<ItemPagamentoRegistrado> linhas =
                jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapear(rs), parametros.toArray());

        boolean temProxima = linhas.size() > limite;
        List<ItemPagamentoRegistrado> itens = temProxima ? linhas.subList(0, limite) : linhas;
        Optional<String> proximoCursor = temProxima
                ? Optional.of(Cursor.de(itens.get(itens.size() - 1)).codificar())
                : Optional.empty();
        return new PaginaPagamentosRegistrados(itens, proximoCursor);
    }

    private static ItemPagamentoRegistrado mapear(ResultSet rs) throws SQLException {
        return new ItemPagamentoRegistrado(
                new PagamentoId(rs.getObject("id", UUID.class)),
                new LiquidacaoId(rs.getObject("liquidacao_id", UUID.class)),
                new Dinheiro(rs.getBigDecimal("valor")),
                rs.getDate("data_competencia").toLocalDate(),
                NaturezaPagamento.valueOf(rs.getString("natureza").toUpperCase()),
                rs.getString("historico"));
    }

    /** Cursor keyset opaco: {@code data_competencia|pagamentoId}, Base64URL. */
    private record Cursor(LocalDate data, UUID id) {

        static Cursor de(ItemPagamentoRegistrado item) {
            return new Cursor(item.dataCompetencia(), item.id().valor());
        }

        String codificar() {
            String bruto = data + "|" + id;
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
