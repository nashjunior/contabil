package br.contabil.razao.infra;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.application.PeriodoContabilPort;
import br.contabil.razao.domain.PeriodoEncerradoException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
    public UUID periodoAbertoPara(TenantId enteId, LocalDate dataCompetencia) {
        List<LinhaPeriodo> linhas = jdbcTemplate.query(
                SQL_PERIODO,
                (rs, rowNum) -> new LinhaPeriodo(rs.getObject("id", UUID.class), rs.getString("status")),
                enteId.valor(),
                dataCompetencia.getYear(),
                dataCompetencia.getMonthValue());

        if (linhas.isEmpty() || !"aberto".equals(linhas.get(0).status())) {
            throw new PeriodoEncerradoException(enteId, dataCompetencia);
        }
        return linhas.get(0).id();
    }

    private record LinhaPeriodo(UUID id, String status) {}
}
