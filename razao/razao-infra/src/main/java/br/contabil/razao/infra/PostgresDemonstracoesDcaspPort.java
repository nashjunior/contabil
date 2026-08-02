package br.contabil.razao.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.BalancoFinanceiro;
import br.contabil.razao.domain.BalancoOrcamentario;
import br.contabil.razao.domain.BalancoPatrimonial;
import br.contabil.razao.domain.DemonstracoesDcasp;
import br.contabil.razao.domain.Dvp;
import br.contabil.razao.domain.LinhaDemonstracaoDcasp;
import br.contabil.razao.domain.repository.DemonstracoesDcaspPort;

/**
 * Adapter Postgres das demonstrações DCASP (ADR-0047). As linhas são derivadas
 * diretamente do razão por ente/exercício, sem snapshot materializado mutável.
 */
@Component
public class PostgresDemonstracoesDcaspPort implements DemonstracoesDcaspPort {

    private static final String FILTRO_ORCAMENTARIO = "c.natureza_informacao = 'orcamentaria'";
    private static final String FILTRO_FINANCEIRO = """
            c.natureza_informacao = 'patrimonial'
            and (c.codigo like '1.1%' or c.codigo like '2.1%')
            """;
    private static final String FILTRO_PATRIMONIAL = """
            c.natureza_informacao = 'patrimonial'
            and (c.codigo like '1%' or c.codigo like '2%')
            """;
    private static final String FILTRO_DVP = """
            c.natureza_informacao = 'patrimonial'
            and (c.codigo like '3%' or c.codigo like '4%')
            """;
    private static final String EXCLUI_ENCERRAMENTO = "f.tipo_evento <> 'encerramento'";
    private static final String SEM_FILTRO_EVENTO = "true";

    private final JdbcTemplate jdbcTemplate;

    public PostgresDemonstracoesDcaspPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public DemonstracoesDcasp demonstracoes(TenantId enteId, int exercicio) {
        Objects.requireNonNull(enteId, "enteId");
        return new DemonstracoesDcasp(
                enteId,
                exercicio,
                new BalancoOrcamentario(enteId, exercicio, movimentoDoExercicio(enteId, exercicio, FILTRO_ORCAMENTARIO)),
                new BalancoFinanceiro(enteId, exercicio, saldoAteExercicio(enteId, exercicio, FILTRO_FINANCEIRO)),
                new BalancoPatrimonial(enteId, exercicio, saldoAteExercicio(enteId, exercicio, FILTRO_PATRIMONIAL)),
                new Dvp(enteId, exercicio, movimentoDoExercicio(enteId, exercicio, FILTRO_DVP)));
    }

    private List<LinhaDemonstracaoDcasp> movimentoDoExercicio(TenantId enteId, int exercicio, String filtroContas) {
        return consultar(sqlMovimento(filtroContas), enteId, exercicio);
    }

    private List<LinhaDemonstracaoDcasp> saldoAteExercicio(TenantId enteId, int exercicio, String filtroContas) {
        return consultar(sqlSaldoAteExercicio(filtroContas), enteId, exercicio);
    }

    private List<LinhaDemonstracaoDcasp> consultar(String sql, TenantId enteId, int exercicio) {
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapearLinha(rs),
                exercicio,
                enteId.valor());
    }

    private static LinhaDemonstracaoDcasp mapearLinha(ResultSet rs) throws SQLException {
        Map<String, Dinheiro> valores = new LinkedHashMap<>();
        valores.put("exercicioAtual", new Dinheiro(rs.getBigDecimal("exercicio_atual")));
        valores.put("exercicioAnterior", new Dinheiro(rs.getBigDecimal("exercicio_anterior")));
        valores.put("movimentoDebito", new Dinheiro(rs.getBigDecimal("movimento_debito")));
        valores.put("movimentoCredito", new Dinheiro(rs.getBigDecimal("movimento_credito")));
        return new LinhaDemonstracaoDcasp(rs.getString("codigo"), rs.getString("descricao"), valores);
    }

    private static String sqlMovimento(String filtroContas) {
        return sqlBase(
                """
                p.exercicio = (select exercicio from parametros)
                """,
                """
                p.exercicio = (select exercicio - 1 from parametros)
                """,
                """
                p.exercicio in ((select exercicio from parametros), (select exercicio - 1 from parametros))
                """,
                filtroContas,
                EXCLUI_ENCERRAMENTO);
    }

    private static String sqlSaldoAteExercicio(String filtroContas) {
        return sqlBase(
                """
                p.exercicio <= (select exercicio from parametros)
                """,
                """
                p.exercicio <= (select exercicio - 1 from parametros)
                """,
                """
                p.exercicio <= (select exercicio from parametros)
                """,
                filtroContas,
                SEM_FILTRO_EVENTO);
    }

    /**
     * {@code filtroEvento} exclui o fato de ENCERRAMENTO do "movimento do exercício"
     * (RAZ-262): o mês 13 tem o mesmo {@code exercicio} dos meses 1-12 e o fato de
     * encerramento cancelaria o saldo apurado no ano se entrasse na soma. O saldo
     * acumulado ({@code saldoAteExercicio}, Balanço Financeiro/Patrimonial) não usa
     * esse filtro pois deve refletir o efeito do encerramento na conta de PL.
     */
    private static String sqlBase(
            String filtroAtual, String filtroAnterior, String filtroPeriodo, String filtroContas, String filtroEvento) {
        return """
                with parametros as (
                  select ?::int as exercicio
                )
                select c.codigo, c.descricao,
                       coalesce(sum(case
                         when (%s) and l.natureza = c.natureza_saldo then l.valor
                         when (%s) then -l.valor
                         else 0
                       end), 0) as exercicio_atual,
                       coalesce(sum(case
                         when (%s) and l.natureza = c.natureza_saldo then l.valor
                         when (%s) then -l.valor
                         else 0
                       end), 0) as exercicio_anterior,
                       coalesce(sum(l.valor) filter (
                         where p.exercicio = (select exercicio from parametros) and l.natureza = 'D'
                       ), 0) as movimento_debito,
                       coalesce(sum(l.valor) filter (
                         where p.exercicio = (select exercicio from parametros) and l.natureza = 'C'
                       ), 0) as movimento_credito
                  from lancamento l
                  join fato_contabil f on f.ente_id = l.ente_id and f.id = l.fato_id
                  join periodo_contabil p on p.ente_id = f.ente_id and p.id = f.periodo_id
                  join conta_pcasp c on c.ente_id = l.ente_id and c.id = l.conta_id
                 where l.ente_id = ?
                   and (%s)
                   and c.escrituravel = true
                   and (%s)
                   and (%s)
                 group by c.codigo, c.descricao
                having coalesce(sum(case when (%s) and l.natureza = c.natureza_saldo then l.valor when (%s) then -l.valor else 0 end), 0) <> 0
                    or coalesce(sum(case when (%s) and l.natureza = c.natureza_saldo then l.valor when (%s) then -l.valor else 0 end), 0) <> 0
                    or coalesce(sum(l.valor) filter (
                         where p.exercicio = (select exercicio from parametros) and l.natureza = 'D'
                       ), 0) <> 0
                    or coalesce(sum(l.valor) filter (
                         where p.exercicio = (select exercicio from parametros) and l.natureza = 'C'
                       ), 0) <> 0
                 order by c.codigo
                """
                .formatted(
                        filtroAtual,
                        filtroAtual,
                        filtroAnterior,
                        filtroAnterior,
                        filtroPeriodo,
                        filtroContas,
                        filtroEvento,
                        filtroAtual,
                        filtroAtual,
                        filtroAnterior,
                        filtroAnterior);
    }
}
