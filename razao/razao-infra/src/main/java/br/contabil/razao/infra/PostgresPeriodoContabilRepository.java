package br.contabil.razao.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.PeriodoContabil;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.StatusPeriodo;
import br.contabil.razao.domain.repository.PeriodoContabilRepository;

/** Adapter Postgres do ciclo de vida do período contábil (RAZ-206, ADR-0045). */
@Repository
public class PostgresPeriodoContabilRepository implements PeriodoContabilRepository {

    private static final String SQL_BUSCAR =
            "select id, exercicio, mes, status, encerrado_em from periodo_contabil"
                    + " where ente_id = ? and exercicio = ? and mes = ?";

    private static final String SQL_ENCERRAR =
            "update periodo_contabil set status = 'encerrado', encerrado_em = ?"
                    + " where ente_id = ? and id = ? and status = 'aberto'";

    private final JdbcTemplate jdbcTemplate;

    public PostgresPeriodoContabilRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PeriodoContabil> buscarPorCompetencia(TenantId enteId, int exercicio, int mes) {
        List<PeriodoContabil> linhas = jdbcTemplate.query(
                SQL_BUSCAR, (rs, rowNum) -> mapear(enteId, rs), enteId.valor(), exercicio, mes);
        return linhas.stream().findFirst();
    }

    @Override
    public boolean encerrar(PeriodoContabil periodo) {
        int atualizadas = jdbcTemplate.update(
                SQL_ENCERRAR,
                Timestamp.from(periodo.encerradoEm().orElseThrow()),
                periodo.enteId().valor(),
                periodo.id().valor());
        return atualizadas > 0;
    }

    private static PeriodoContabil mapear(TenantId enteId, ResultSet rs) throws SQLException {
        Timestamp encerradoEm = rs.getTimestamp("encerrado_em");
        return PeriodoContabil.reidratar(
                new PeriodoContabilId(rs.getObject("id", UUID.class)),
                enteId,
                rs.getInt("exercicio"),
                rs.getInt("mes"),
                StatusPeriodo.deCodigo(rs.getString("status")),
                Optional.ofNullable(encerradoEm).map(Timestamp::toInstant));
    }
}
