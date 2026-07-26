package br.contabil.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.ExecucaoOrcamentariaPeriodo;
import br.contabil.execucao.infra.PostgresExecucaoOrcamentariaPeriodoPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/**
 * Conformidade da execução orçamentária agregada por período/ente (RAZ-97) contra Postgres
 * real (Testcontainers) — extensão do gold set (RAZ-19/RAZ-84): prova que
 * {@link PostgresExecucaoOrcamentariaPeriodoPort} soma empenhado/liquidado/pago EXATAMENTE
 * dos registros reais de {@code empenho}/{@code liquidacao}/{@code pagamento} dentro do
 * intervalo "1º de janeiro do exercício até o fim do mês pedido" (leitura "até o
 * bimestre/mês" do RREO), inclusive nas bordas de data e na virada de exercício — o que os
 * testes de unidade de {@code ConsultarExecucaoOrcamentaria}/{@code ExecucaoOrcamentariaPeriodo}
 * não alcançam por serem mockados/construídos em memória — e o isolamento RLS entre entes.
 */
@Testcontainers(disabledWithoutDocker = true)
class ExecucaoOrcamentariaPeriodoConformidadeIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static String enteA;
    private static String enteB;
    private static String periodoFkA;
    private static String periodoFkB;

    @BeforeAll
    static void preparaBase() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection admin = adminConnection();
                Statement st = admin.createStatement()) {
            st.execute("create role app_login login password 'app_login'");
            st.execute("grant app_role to app_login");
        }

        enteA = criarEnte("55555555555501", "Ente Execucao Periodo A");
        enteB = criarEnte("55555555555502", "Ente Execucao Periodo B");
        // periodo_contabil só ancora a FK de fato_contabil — a agregação por período/ente
        // filtra por data_fato/data_competencia direto (execucao-orcamentaria-despesa.md
        // §Fronteiras), não por periodo_id, por isso um único período serve de âncora para
        // fatos com datas de negócio espalhadas por vários meses/exercícios nos testes abaixo.
        periodoFkA = criarPeriodo(enteA, 2026, 1);
        periodoFkB = criarPeriodo(enteB, 2026, 1);
    }

    // ------------------------------------------------------------------
    // 1. Totais batem com a soma dos registros reais no intervalo "1º jan
    //    do exercício até o fim do mês pedido"; borda superior inclusiva
    //    (último dia do mês) e exclusiva (1º dia do mês seguinte); registro
    //    do exercício anterior (mesmo que cronologicamente próximo) fica
    //    de fora.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("totais empenhado/liquidado/pago batem com os registros reais no intervalo do período pedido")
    void totaisBatemComOsRegistrosReaisDoIntervaloDoPeriodo() throws SQLException {
        String dotacao = criarDotacao(enteA, "10000.00");

        String empenho1 = criarEmpenho(enteA, dotacao, periodoFkA, "1000.00", "2026-01-15", 1L);
        criarEmpenho(enteA, dotacao, periodoFkA, "500.00", "2026-03-31", 2L); // último dia do mês alvo: incluído
        criarEmpenho(enteA, dotacao, periodoFkA, "300.00", "2026-04-01", 3L); // 1º dia do mês seguinte: excluído
        criarEmpenho(enteA, dotacao, periodoFkA, "200.00", "2025-12-31", 4L); // exercício anterior: excluído

        String liquidacao1 = criarLiquidacao(enteA, empenho1, periodoFkA, "400.00", "2026-02-10", 5L);
        criarLiquidacao(enteA, empenho1, periodoFkA, "999.00", "2026-04-01", 6L); // fora do intervalo: excluído

        criarPagamento(enteA, liquidacao1, periodoFkA, "150.00", "2026-01-20", 7L);
        criarPagamento(enteA, liquidacao1, periodoFkA, "50.00", "2025-12-31", 8L); // fora do intervalo: excluído

        ExecucaoOrcamentariaPeriodo execucao = consultarExecucao(enteA, enteA, 2026, 3);

        assertThat(execucao.totalEmpenhado())
                .as("1000 (15/01) + 500 (31/03, borda inclusiva) — exclui 300 (01/04) e 200 (exercício anterior)")
                .isEqualTo(Dinheiro.de("1500.00"));
        assertThat(execucao.totalLiquidado())
                .as("400 (10/02) — exclui 999 (01/04, fora do intervalo)")
                .isEqualTo(Dinheiro.de("400.00"));
        assertThat(execucao.totalPago())
                .as("150 (20/01) — exclui 50 (exercício anterior)")
                .isEqualTo(Dinheiro.de("150.00"));
        assertThat(execucao.saldoALiquidar()).isEqualTo(Dinheiro.de("1100.00"));
        assertThat(execucao.saldoAPagar()).isEqualTo(Dinheiro.de("250.00"));
    }

    // ------------------------------------------------------------------
    // 2. Sem execução alguma no período pedido: totais e saldos zero, sem
    //    erro (read-model derivado — zero não é um caso de erro).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("período sem nenhum registro devolve totais e saldos zero")
    void periodoSemExecucaoAlgumaDevolveTotaisZero() throws SQLException {
        ExecucaoOrcamentariaPeriodo execucao = consultarExecucao(enteA, enteA, 2019, 5);

        assertThat(execucao.totalEmpenhado()).isEqualTo(Dinheiro.zero());
        assertThat(execucao.totalLiquidado()).isEqualTo(Dinheiro.zero());
        assertThat(execucao.totalPago()).isEqualTo(Dinheiro.zero());
        assertThat(execucao.saldoALiquidar()).isEqualTo(Dinheiro.zero());
        assertThat(execucao.saldoAPagar()).isEqualTo(Dinheiro.zero());
    }

    // ------------------------------------------------------------------
    // 3. RLS: a execução agregada de um ente nunca soma registros de
    //    outro, mesmo que o parâmetro de consulta divirja da sessão.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("execução agregada nunca soma registros de outro ente, mesmo com enteId de consulta divergente da sessão RLS")
    void execucaoRespeitaIsolamentoMultiTenantMesmoComEnteIdDivergenteDaSessao() throws SQLException {
        String dotacaoA = criarDotacao(enteA, "5000.00");
        String empenhoA = criarEmpenho(enteA, dotacaoA, periodoFkA, "1000.00", "2026-06-10", 10L);
        String liquidacaoA = criarLiquidacao(enteA, empenhoA, periodoFkA, "600.00", "2026-06-11", 11L);
        criarPagamento(enteA, liquidacaoA, periodoFkA, "200.00", "2026-06-12", 12L);

        String dotacaoB = criarDotacao(enteB, "5000.00");
        String empenhoB = criarEmpenho(enteB, dotacaoB, periodoFkB, "850.00", "2026-06-10", 10L);
        String liquidacaoB = criarLiquidacao(enteB, empenhoB, periodoFkB, "300.00", "2026-06-11", 11L);
        criarPagamento(enteB, liquidacaoB, periodoFkB, "100.00", "2026-06-12", 12L);

        ExecucaoOrcamentariaPeriodo execucaoA = consultarExecucao(enteA, enteA, 2026, 6);
        assertThat(execucaoA.totalEmpenhado()).isEqualTo(Dinheiro.de("1000.00"));
        assertThat(execucaoA.totalLiquidado()).isEqualTo(Dinheiro.de("600.00"));
        assertThat(execucaoA.totalPago()).isEqualTo(Dinheiro.de("200.00"));

        ExecucaoOrcamentariaPeriodo execucaoBVistoDeB = consultarExecucao(enteB, enteB, 2026, 6);
        assertThat(execucaoBVistoDeB.totalEmpenhado())
                .as("ente B tem dados reais e recuperáveis na própria sessão")
                .isEqualTo(Dinheiro.de("850.00"));

        ExecucaoOrcamentariaPeriodo execucaoBVistoDeSessaoA = consultarExecucao(enteA, enteB, 2026, 6);
        assertThat(execucaoBVistoDeSessaoA.totalEmpenhado())
                .as("RLS bloqueia mesmo quando o parâmetro de consulta pede o ente B mas a sessão está setada para o ente A")
                .isEqualTo(Dinheiro.zero());
        assertThat(execucaoBVistoDeSessaoA.totalLiquidado()).isEqualTo(Dinheiro.zero());
        assertThat(execucaoBVistoDeSessaoA.totalPago()).isEqualTo(Dinheiro.zero());
    }

    // ------------------------------------------------------------------
    // 4. Regressão: mês fora de 1-12 é rejeitado por ExecucaoInvalidaException
    //    já na porta Postgres real — não estoura DateTimeException não
    //    mapeada (achado real corrigido no RAZ-97).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("mês fora de 1-12 lança ExecucaoInvalidaException na porta real, não DateTimeException")
    void mesForaDoIntervaloRejeitadoAntesDeMontarLocalDate() throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            try (Statement st = conexao.createStatement()) {
                st.execute("set local app.ente_id = '" + enteA + "'");
            }
            PostgresExecucaoOrcamentariaPeriodoPort port =
                    new PostgresExecucaoOrcamentariaPeriodoPort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));

            assertThatThrownBy(() -> port.execucaoDoPeriodo(TenantId.de(enteA), 2026, 13))
                    .isInstanceOfSatisfying(
                            ExecucaoInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("periodo_invalido"));

            conexao.rollback();
        }
    }

    // ------------------------------------------------------------------
    // Infraestrutura do teste
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface OperacaoAppLogin<T> {
        T executar(Statement st) throws SQLException;
    }

    private static <T> T comoAppLogin(String enteSessao, OperacaoAppLogin<T> operacao) throws SQLException {
        try (Connection conn = appLoginConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("set local app.ente_id = '" + enteSessao + "'");
                T resultado = operacao.executar(st);
                conn.commit();
                return resultado;
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection appLoginConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
    }

    private static String criarEnte(String cnpj, String nome) throws SQLException {
        try (Connection admin = adminConnection();
                Statement st = admin.createStatement();
                ResultSet rs = st.executeQuery(
                        "insert into ente (cnpj, nome, esfera) values ('%s','%s','municipio') returning id"
                                .formatted(cnpj, nome))) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static String criarPeriodo(String enteId, int exercicio, int mes) throws SQLException {
        return comoAppLogin(enteId, st -> {
            try (ResultSet rs = st.executeQuery(
                    "insert into periodo_contabil (ente_id, exercicio, mes, status) values ('%s',%d,%d,'aberto') returning id"
                            .formatted(enteId, exercicio, mes))) {
                rs.next();
                return rs.getString(1);
            }
        });
    }

    private static String criarDotacao(String enteId, String valorAutorizado) throws SQLException {
        return comoAppLogin(enteId, st -> {
            try (ResultSet rs = st.executeQuery(("insert into dotacao (ente_id, exercicio, "
                    + "classificacao_orcamentaria, fonte_recurso, unidade_gestora_id, valor_autorizado) "
                    + "values ('%s', 2026, 'teste-execucao-periodo', 'teste-execucao-periodo', '%s', %s) returning id")
                    .formatted(enteId, UUID.randomUUID(), valorAutorizado))) {
                rs.next();
                return rs.getString(1);
            }
        });
    }

    private static UUID criarFato(JdbcTemplate jdbcTemplate, String enteId, String periodoId, long numeroSeq, String tipoEvento) {
        UUID fatoContabilId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, "
                        + "tipo_evento, historico, origem) values (?,?,?,current_date,?,?,?,?)",
                fatoContabilId,
                UUID.fromString(enteId),
                numeroSeq,
                UUID.fromString(periodoId),
                tipoEvento,
                "fato teste execucao periodo",
                "teste-execucao-periodo:%s:%s".formatted(tipoEvento, fatoContabilId));
        return fatoContabilId;
    }

    private static String criarEmpenho(String enteId, String dotacaoId, String periodoFkId, String valor, String dataFatoIso,
            long numeroSeq) throws SQLException {
        return comoAppLogin(enteId, st -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(st.getConnection(), true));
            UUID fatoContabilId = criarFato(jdbcTemplate, enteId, periodoFkId, numeroSeq, "empenho");
            try (ResultSet rs = st.executeQuery(("insert into empenho (ente_id, numero_sequencial, exercicio, "
                    + "tipo, dotacao_id, credor_id, unidade_gestora_id, valor, data_fato, "
                    + "classificacao_orcamentaria, fonte_recurso, historico, fato_contabil_id) "
                    + "values ('%s', %d, %d, 'ordinario', '%s', '%s', '%s', %s, '%s', "
                    + "'teste-execucao-periodo', 'teste-execucao-periodo', 'empenho teste execucao periodo', '%s') returning id")
                    .formatted(
                            enteId,
                            numeroSeq,
                            Integer.parseInt(dataFatoIso.substring(0, 4)),
                            dotacaoId,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            valor,
                            dataFatoIso,
                            fatoContabilId))) {
                rs.next();
                return rs.getString(1);
            }
        });
    }

    private static String criarLiquidacao(String enteId, String empenhoId, String periodoFkId, String valor,
            String dataCompetenciaIso, long numeroSeq) throws SQLException {
        return comoAppLogin(enteId, st -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(st.getConnection(), true));
            UUID fatoContabilId = criarFato(jdbcTemplate, enteId, periodoFkId, numeroSeq, "liquidacao");
            try (ResultSet rs = st.executeQuery(("insert into liquidacao (ente_id, empenho_id, data_competencia, "
                    + "valor, documentos_suporte, historico, fato_contabil_id) "
                    + "values ('%s', '%s', '%s', %s, '[{\"tipo\":\"NF\",\"numero\":\"%d\"}]', "
                    + "'liquidacao teste execucao periodo', '%s') returning id")
                    .formatted(enteId, empenhoId, dataCompetenciaIso, valor, numeroSeq, fatoContabilId))) {
                rs.next();
                return rs.getString(1);
            }
        });
    }

    private static String criarPagamento(String enteId, String liquidacaoId, String periodoFkId, String valor,
            String dataCompetenciaIso, long numeroSeq) throws SQLException {
        return comoAppLogin(enteId, st -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(st.getConnection(), true));
            UUID fatoContabilId = criarFato(jdbcTemplate, enteId, periodoFkId, numeroSeq, "pagamento");
            try (ResultSet rs = st.executeQuery(("insert into pagamento (ente_id, liquidacao_id, data_competencia, "
                    + "valor, natureza, beneficiario_nome, beneficiario_cpf_cnpj, historico, fato_contabil_id) "
                    + "values ('%s', '%s', '%s', %s, 'orcamentario', 'Fornecedor Teste', "
                    + "'12345678901', 'pagamento teste execucao periodo', '%s') returning id")
                    .formatted(enteId, liquidacaoId, dataCompetenciaIso, valor, fatoContabilId))) {
                rs.next();
                return rs.getString(1);
            }
        });
    }

    private static ExecucaoOrcamentariaPeriodo consultarExecucao(String enteSessao, String enteConsulta, int exercicio, int mes)
            throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            try (Statement st = conexao.createStatement()) {
                st.execute("set local app.ente_id = '" + enteSessao + "'");
            }
            PostgresExecucaoOrcamentariaPeriodoPort port =
                    new PostgresExecucaoOrcamentariaPeriodoPort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));
            ExecucaoOrcamentariaPeriodo resultado = port.execucaoDoPeriodo(TenantId.de(enteConsulta), exercicio, mes);
            conexao.commit();
            return resultado;
        }
    }
}
