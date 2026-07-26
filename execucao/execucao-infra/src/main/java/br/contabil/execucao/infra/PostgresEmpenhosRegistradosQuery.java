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
import br.contabil.execucao.domain.ItemEmpenhoRegistrado;
import br.contabil.execucao.domain.PaginaEmpenhosRegistrados;
import br.contabil.execucao.domain.StatusEmpenho;
import br.contabil.execucao.domain.TipoEmpenho;
import br.contabil.execucao.domain.repository.EmpenhosRegistradosQuery;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/**
 * Adapter Postgres do read model do registro de empenhos (RAZ-121; JdbcTemplate,
 * mesma escolha do resto de {@code execucao-infra}).
 *
 * <p>Paginação por keyset {@code (data_fato, id)} <b>descendente</b> — ao
 * contrário da fila de aprovação ({@link PostgresFilaAprovacaoQuery}, mais
 * antigos primeiro por ser uma fila de trabalho), o registro é uma tela de
 * auditoria: mais recentes primeiro. Cursor opaco Base64URL de {@code
 * dataFato|id}. Busca {@code limite + 1} para saber se há próxima página sem
 * {@code COUNT(*)} da tabela inteira (ADR-0007).
 */
@Repository
public class PostgresEmpenhosRegistradosQuery implements EmpenhosRegistradosQuery {

    private static final String SQL_BASE =
            """
            select id, numero_sequencial, exercicio, tipo, credor_id, valor, data_fato, historico, status
              from empenho
             where ente_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresEmpenhosRegistradosQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public PaginaEmpenhosRegistrados consultar(
            TenantId enteId,
            Optional<Integer> exercicio,
            Optional<LocalDate> dataInicio,
            Optional<LocalDate> dataFim,
            int limite,
            Optional<String> cursor) {
        Objects.requireNonNull(enteId, "enteId");
        Objects.requireNonNull(exercicio, "exercicio (Optional, nunca null)");
        Objects.requireNonNull(dataInicio, "dataInicio (Optional, nunca null)");
        Objects.requireNonNull(dataFim, "dataFim (Optional, nunca null)");
        Objects.requireNonNull(cursor, "cursor (Optional, nunca null)");

        StringBuilder sql = new StringBuilder(SQL_BASE);
        List<Object> parametros = new ArrayList<>();
        parametros.add(enteId.valor());

        exercicio.ifPresent(valor -> {
            sql.append(" and exercicio = ?");
            parametros.add(valor);
        });
        dataInicio.ifPresent(valor -> {
            sql.append(" and data_fato >= ?");
            parametros.add(valor);
        });
        dataFim.ifPresent(valor -> {
            sql.append(" and data_fato <= ?");
            parametros.add(valor);
        });
        cursor.ifPresent(opaco -> {
            Cursor decodificado = Cursor.decodificar(opaco);
            sql.append(" and (data_fato, id) < (?, ?)");
            parametros.add(decodificado.data());
            parametros.add(decodificado.id());
        });

        sql.append(" order by data_fato desc, id desc limit ?");
        parametros.add(limite + 1);

        List<ItemEmpenhoRegistrado> linhas =
                jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapear(rs), parametros.toArray());

        boolean temProxima = linhas.size() > limite;
        List<ItemEmpenhoRegistrado> itens = temProxima ? linhas.subList(0, limite) : linhas;
        Optional<String> proximoCursor = temProxima
                ? Optional.of(Cursor.de(itens.get(itens.size() - 1)).codificar())
                : Optional.empty();
        return new PaginaEmpenhosRegistrados(itens, proximoCursor);
    }

    private static ItemEmpenhoRegistrado mapear(ResultSet rs) throws SQLException {
        return new ItemEmpenhoRegistrado(
                new EmpenhoId(rs.getObject("id", UUID.class)),
                rs.getLong("numero_sequencial"),
                rs.getInt("exercicio"),
                TipoEmpenho.deCodigo(rs.getString("tipo")),
                new CredorId(rs.getObject("credor_id", UUID.class)),
                new Dinheiro(rs.getBigDecimal("valor")),
                rs.getDate("data_fato").toLocalDate(),
                rs.getString("historico"),
                StatusEmpenho.valueOf(rs.getString("status").toUpperCase(java.util.Locale.ROOT)));
    }

    /** Cursor keyset opaco: {@code data_fato|empenhoId}, Base64URL. */
    private record Cursor(LocalDate data, UUID id) {

        static Cursor de(ItemEmpenhoRegistrado item) {
            return new Cursor(item.dataFato(), item.id().valor());
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
