package br.contabil.golden;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.SaldoEmpenho;
import br.contabil.execucao.domain.SaldoLiquidacao;
import br.contabil.execucao.infra.PostgresSaldosExecucaoPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/** RAZ-96: prova os saldos derivados de empenho/liquidação contra Postgres real. */
@Testcontainers(disabledWithoutDocker = true)
class ExecucaoSaldosEstagiosIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static String enteId;
    private static String periodoId;
    private static String dotacaoId;
    private static String empenhoId;
    private static String liquidacaoId;

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
            try (ResultSet rs = st.executeQuery("insert into ente (cnpj, nome, esfera) "
                    + "values ('55555555555555', 'Ente Saldos Execucao', 'municipio') returning id")) {
                rs.next();
                enteId = rs.getString(1);
            }
        }

        periodoId = criarPeriodo();
        dotacaoId = criarDotacao("1000.00");
        empenhoId = criarEmpenho("1000.00", 1L);
        liquidacaoId = criarLiquidacao(empenhoId, "250.00", 2L);
        criarLiquidacao(empenhoId, "125.00", 3L);
        criarPagamento(liquidacaoId, "70.00", 4L);
        criarPagamento(liquidacaoId, "30.00", 5L);
    }

    @Test
    void saldoEmpenhoESaldoLiquidacaoSaoDerivadosDosFilhosPersistidos() throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            PostgresSaldosExecucaoPort port =
                    new PostgresSaldosExecucaoPort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));

            SaldoEmpenho saldoEmpenho = port.saldoEmpenho(TenantId.de(enteId), EmpenhoId.de(empenhoId));
            SaldoLiquidacao saldoLiquidacao =
                    port.saldoLiquidacao(TenantId.de(enteId), LiquidacaoId.de(liquidacaoId));

            assertThat(saldoEmpenho.valorEmpenhado()).isEqualTo(Dinheiro.de("1000.00"));
            assertThat(saldoEmpenho.valorLiquidado()).isEqualTo(Dinheiro.de("375.00"));
            assertThat(saldoEmpenho.saldoALiquidar()).isEqualTo(Dinheiro.de("625.00"));

            assertThat(saldoLiquidacao.valorLiquidado()).isEqualTo(Dinheiro.de("250.00"));
            assertThat(saldoLiquidacao.valorPago()).isEqualTo(Dinheiro.de("100.00"));
            assertThat(saldoLiquidacao.saldoAPagar()).isEqualTo(Dinheiro.de("150.00"));
            conexao.commit();
        }
    }

    @Test
    void saldosRejeitamEstagioInexistenteDoEnte() throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            PostgresSaldosExecucaoPort port =
                    new PostgresSaldosExecucaoPort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));

            assertThatThrownBy(() -> port.saldoEmpenho(TenantId.de(enteId), EmpenhoId.novo()))
                    .isInstanceOfSatisfying(
                            ExecucaoInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("empenho_nao_encontrado"));
            assertThatThrownBy(() -> port.saldoLiquidacao(TenantId.de(enteId), LiquidacaoId.novo()))
                    .isInstanceOfSatisfying(
                            ExecucaoInvalidaException.class,
                            erro -> assertThat(erro.codigo()).isEqualTo("liquidacao_nao_encontrada"));
            conexao.rollback();
        }
    }

    @Test
    void saldoEmpenhoTravaLinhaDoEmpenhoAteCommit() throws Exception {
        assertConsultaFicaBloqueadaEnquantoPrimeiraTransacaoSeguraLock(() -> {
            Connection conexao = appLoginConnection();
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            PostgresSaldosExecucaoPort port =
                    new PostgresSaldosExecucaoPort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));
            port.saldoEmpenho(TenantId.de(enteId), EmpenhoId.de(empenhoId));
            return conexao;
        }, () -> {
            try (Connection conexao = appLoginConnection()) {
                conexao.setAutoCommit(false);
                setEnteDaSessao(conexao);
                PostgresSaldosExecucaoPort port =
                        new PostgresSaldosExecucaoPort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));
                port.saldoEmpenho(TenantId.de(enteId), EmpenhoId.de(empenhoId));
                conexao.commit();
            }
        });
    }

    @Test
    void saldoLiquidacaoTravaLinhaDaLiquidacaoAteCommit() throws Exception {
        assertConsultaFicaBloqueadaEnquantoPrimeiraTransacaoSeguraLock(() -> {
            Connection conexao = appLoginConnection();
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            PostgresSaldosExecucaoPort port =
                    new PostgresSaldosExecucaoPort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));
            port.saldoLiquidacao(TenantId.de(enteId), LiquidacaoId.de(liquidacaoId));
            return conexao;
        }, () -> {
            try (Connection conexao = appLoginConnection()) {
                conexao.setAutoCommit(false);
                setEnteDaSessao(conexao);
                PostgresSaldosExecucaoPort port =
                        new PostgresSaldosExecucaoPort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));
                port.saldoLiquidacao(TenantId.de(enteId), LiquidacaoId.de(liquidacaoId));
                conexao.commit();
            }
        });
    }

    private static void assertConsultaFicaBloqueadaEnquantoPrimeiraTransacaoSeguraLock(
            ConsultaBloqueante primeiraConsulta, ThrowingRunnable segundaConsulta) throws Exception {
        CountDownLatch primeiraTravaAdquirida = new CountDownLatch(1);
        CountDownLatch podeLiberarTrava = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> primeira = executor.submit(() -> {
                try (Connection conexao = primeiraConsulta.executar()) {
                    primeiraTravaAdquirida.countDown();
                    podeLiberarTrava.await(5, TimeUnit.SECONDS);
                    conexao.commit();
                }
                return null;
            });

            assertThat(primeiraTravaAdquirida.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Void> segunda = executor.submit(() -> {
                segundaConsulta.executar();
                return null;
            });

            assertThatThrownBy(() -> segunda.get(500, TimeUnit.MILLISECONDS))
                    .as("segunda consulta deve bloquear no select ... for update até a primeira comitar")
                    .isInstanceOf(TimeoutException.class);

            podeLiberarTrava.countDown();
            primeira.get(5, TimeUnit.SECONDS);
            segunda.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static String criarPeriodo() throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(
                            ("insert into periodo_contabil (ente_id, exercicio, mes, status) "
                                    + "values ('%s', 2026, 1, 'aberto') returning id")
                                    .formatted(enteId))) {
                rs.next();
                String id = rs.getString(1);
                conexao.commit();
                return id;
            }
        }
    }

    private static String criarDotacao(String valorAutorizado) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(("insert into dotacao (ente_id, exercicio, "
                            + "classificacao_orcamentaria, fonte_recurso, unidade_gestora_id, valor_autorizado) "
                            + "values ('%s', 2026, 'teste-saldos', 'teste-saldos', '%s', %s) returning id")
                            .formatted(enteId, UUID.randomUUID(), valorAutorizado))) {
                rs.next();
                String id = rs.getString(1);
                conexao.commit();
                return id;
            }
        }
    }

    private static String criarEmpenho(String valor, long numeroSeq) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(conexao, true));
            UUID fatoContabilId = criarFato(jdbcTemplate, numeroSeq, "empenho");
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(("insert into empenho (ente_id, numero_sequencial, exercicio, "
                            + "tipo, dotacao_id, credor_id, unidade_gestora_id, valor, data_fato, "
                            + "classificacao_orcamentaria, fonte_recurso, historico, fato_contabil_id) "
                            + "values ('%s', %d, 2026, 'ordinario', '%s', '%s', '%s', %s, current_date, "
                            + "'teste-saldos', 'teste-saldos', 'empenho teste saldos', '%s') returning id")
                            .formatted(
                                    enteId,
                                    numeroSeq,
                                    dotacaoId,
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    valor,
                                    fatoContabilId))) {
                rs.next();
                String id = rs.getString(1);
                conexao.commit();
                return id;
            }
        }
    }

    private static String criarLiquidacao(String empenhoIdParam, String valor, long numeroSeq) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(conexao, true));
            UUID fatoContabilId = criarFato(jdbcTemplate, numeroSeq, "liquidacao");
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(("insert into liquidacao (ente_id, empenho_id, data_competencia, "
                            + "valor, documentos_suporte, historico, fato_contabil_id) "
                            + "values ('%s', '%s', current_date, %s, '[{\"tipo\":\"NF\",\"numero\":\"%d\"}]', "
                            + "'liquidacao teste saldos', '%s') returning id")
                            .formatted(enteId, empenhoIdParam, valor, numeroSeq, fatoContabilId))) {
                rs.next();
                String id = rs.getString(1);
                conexao.commit();
                return id;
            }
        }
    }

    private static String criarPagamento(String liquidacaoIdParam, String valor, long numeroSeq) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(conexao, true));
            UUID fatoContabilId = criarFato(jdbcTemplate, numeroSeq, "pagamento");
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(("insert into pagamento (ente_id, liquidacao_id, data_competencia, "
                            + "valor, natureza, beneficiario_nome, beneficiario_cpf_cnpj, historico, fato_contabil_id) "
                            + "values ('%s', '%s', current_date, %s, 'orcamentario', 'Fornecedor Teste', "
                            + "'12345678901', 'pagamento teste saldos', '%s') returning id")
                            .formatted(enteId, liquidacaoIdParam, valor, fatoContabilId))) {
                rs.next();
                String id = rs.getString(1);
                conexao.commit();
                return id;
            }
        }
    }

    private static UUID criarFato(JdbcTemplate jdbcTemplate, long numeroSeq, String tipoEvento) {
        UUID fatoContabilId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, "
                        + "tipo_evento, historico, origem) values (?,?,?,current_date,?,?,?,?)",
                fatoContabilId,
                UUID.fromString(enteId),
                numeroSeq,
                UUID.fromString(periodoId),
                tipoEvento,
                "fato teste saldos",
                "teste-saldos:%s:%s".formatted(tipoEvento, fatoContabilId));
        return fatoContabilId;
    }

    private static void setEnteDaSessao(Connection conexao) throws SQLException {
        try (Statement st = conexao.createStatement()) {
            st.execute("set local app.ente_id = '" + enteId + "'");
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection appLoginConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
    }

    @FunctionalInterface
    private interface ConsultaBloqueante {
        Connection executar() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void executar() throws Exception;
    }
}
