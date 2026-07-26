package br.contabil.execucao.infra;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.execucao.domain.ExecucaoOrcamentariaPeriodo;
import br.contabil.execucao.domain.repository.ExecucaoOrcamentariaPeriodoPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/**
 * Deriva a execução orçamentária (empenhado/liquidado/pago) direto de
 * {@code empenho}/{@code liquidacao}/{@code pagamento}, acumulada de 1º de
 * janeiro do exercício até o último dia do mês pedido — mesma leitura "até o
 * bimestre/mês" do RREO. Filtra por data ({@code data_fato}/
 * {@code data_competencia}), não por {@code periodo_id}: nenhuma dessas tabelas
 * tem vínculo com {@code periodo_contabil} (só com {@code fato_contabil_id}, para
 * a escrituração no razão) — mantém a consulta inteiramente dentro do próprio
 * bounded context da execução, sem acoplar {@code execucao-infra} ao schema do
 * razão (execucao-orcamentaria-despesa.md §Fronteiras).
 */
@Component
public class PostgresExecucaoOrcamentariaPeriodoPort implements ExecucaoOrcamentariaPeriodoPort {

    private static final String SQL_TOTAL_EMPENHADO =
            "select coalesce(sum(valor), 0) from empenho where ente_id = ? and data_fato >= ? and data_fato < ?";

    private static final String SQL_TOTAL_LIQUIDADO =
            "select coalesce(sum(valor), 0) from liquidacao where ente_id = ? and data_competencia >= ? and data_competencia < ?";

    private static final String SQL_TOTAL_PAGO =
            "select coalesce(sum(valor), 0) from pagamento where ente_id = ? and data_competencia >= ? and data_competencia < ?";

    private final JdbcTemplate jdbcTemplate;

    public PostgresExecucaoOrcamentariaPeriodoPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ExecucaoOrcamentariaPeriodo execucaoDoPeriodo(TenantId enteId, int exercicio, int mes) {
        ExecucaoOrcamentariaPeriodo.validarMes(mes);
        LocalDate inicioExercicio = LocalDate.of(exercicio, 1, 1);
        LocalDate fimPeriodoExclusivo = LocalDate.of(exercicio, mes, 1).plusMonths(1);

        Dinheiro totalEmpenhado = somar(SQL_TOTAL_EMPENHADO, enteId, inicioExercicio, fimPeriodoExclusivo);
        Dinheiro totalLiquidado = somar(SQL_TOTAL_LIQUIDADO, enteId, inicioExercicio, fimPeriodoExclusivo);
        Dinheiro totalPago = somar(SQL_TOTAL_PAGO, enteId, inicioExercicio, fimPeriodoExclusivo);

        return new ExecucaoOrcamentariaPeriodo(enteId, exercicio, mes, totalEmpenhado, totalLiquidado, totalPago);
    }

    private Dinheiro somar(String sql, TenantId enteId, LocalDate inicio, LocalDate fimExclusivo) {
        BigDecimal total = jdbcTemplate.queryForObject(sql, BigDecimal.class, enteId.valor(), inicio, fimExclusivo);
        return new Dinheiro(total);
    }
}
