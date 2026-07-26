package br.contabil.razao.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.PeriodoEncerradoException;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.repository.PeriodoContabilPort;

/**
 * Regra 5: consulta o período contábil da competência informada. Encerrado ou
 * inexistente rejeita com {@link PeriodoEncerradoException} — nunca retorna
 * nulo/booleano (motor-razao-partidas-dobradas.md).
 */
@Component
public class PostgresPeriodoContabilPort implements PeriodoContabilPort {

    private static final String SQL_PERIODO =
            "select id, status from periodo_contabil where ente_id = ? and exercicio = ? and mes = ?";

    private final JdbcTemplate jdbcTemplate;

    public PostgresPeriodoContabilPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PeriodoContabilId periodoAbertoPara(TenantId enteId, LocalDate dataCompetencia) {
        List<LinhaPeriodo> linhas = jdbcTemplate.query(
                SQL_PERIODO,
                (rs, rowNum) -> new LinhaPeriodo(new PeriodoContabilId(rs.getObject("id", UUID.class)), rs.getString("status")),
                enteId.valor(),
                dataCompetencia.getYear(),
                dataCompetencia.getMonthValue());

        if (linhas.isEmpty() || !"aberto".equals(linhas.get(0).status())) {
            throw new PeriodoEncerradoException(enteId, dataCompetencia);
        }
        return linhas.get(0).id();
    }

    private record LinhaPeriodo(PeriodoContabilId id, String status) {}
}
