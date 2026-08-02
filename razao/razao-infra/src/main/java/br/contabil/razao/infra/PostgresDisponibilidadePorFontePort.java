package br.contabil.razao.infra;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FonteRecurso;
import br.contabil.razao.domain.repository.DisponibilidadePorFontePort;

/**
 * Saldo devedor líquido por {@link FonteRecurso} para contas DDR — read-model
 * derivado dos lançamentos (ADR-0007, ADR-0054). Sem compensação entre fontes.
 *
 * <p>Usa {@code = ANY(?)} com array UUID para filtrar as contas de controle
 * num único round-trip; a criação do {@link Array} exige uma {@link Connection}
 * aberta, por isso usamos {@code execute(PreparedStatementCreator, ...)} em vez
 * do form simples do {@link JdbcTemplate}.
 */
@Component
public class PostgresDisponibilidadePorFontePort implements DisponibilidadePorFontePort {

    private static final String SQL = """
            select fonte_recurso,
                   sum(case when natureza = 'D' then valor else -valor end) as saldo
              from lancamento
             where ente_id = ?
               and conta_id = any(?)
               and fonte_recurso is not null
             group by fonte_recurso
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresDisponibilidadePorFontePort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SaldoPorFonte> consultarSaldoPorFonte(TenantId enteId, List<ContaContabilId> contas) {
        if (contas.isEmpty()) {
            return List.of();
        }
        UUID[] ids = contas.stream().map(ContaContabilId::valor).toArray(UUID[]::new);

        return jdbcTemplate.execute(
                (Connection conn) -> conn.prepareStatement(SQL),
                (PreparedStatement ps) -> {
                    ps.setObject(1, enteId.valor());
                    Array arr = ps.getConnection().createArrayOf("uuid", ids);
                    ps.setArray(2, arr);
                    List<SaldoPorFonte> resultado = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            FonteRecurso fonte = FonteRecurso.de(rs.getString("fonte_recurso"));
                            BigDecimal saldo = rs.getBigDecimal("saldo");
                            resultado.add(new SaldoPorFonte(fonte, new Dinheiro(saldo)));
                        }
                    }
                    return resultado;
                });
    }
}
