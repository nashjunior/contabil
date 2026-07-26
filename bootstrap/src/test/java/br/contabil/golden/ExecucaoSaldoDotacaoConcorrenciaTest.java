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

import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.SaldoDotacao;
import br.contabil.execucao.domain.SaldoInsuficienteException;
import br.contabil.execucao.infra.PostgresSaldosExecucaoPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/**
 * RAZ-84: prova, contra Postgres real, a trava de concorrência do UC1
 * ({@code execucao-orcamentaria-despesa.md} — "dois empenhos concorrentes",
 * {@code arquitetura-tecnica/README.md#uc1}) descrita no Javadoc de
 * {@link PostgresSaldosExecucaoPort}: o {@code select ... for update} em
 * {@code saldoDotacao} serializa duas transações que disputam o crédito da
 * MESMA dotação, evitando comprometer além do autorizado (Lei 4.320/1964
 * art. 59).
 *
 * <p>{@code PostgresSaldosExecucaoPort} (RAZ-65/66) não tinha nenhum teste
 * dedicado até esta entrega — só aparecia mockado em
 * {@code RegistrarEmpenhoTest}. Este teste sobe o adapter de verdade contra
 * um Postgres real, sem contexto Spring (mesmo estilo direto do
 * {@link GoldSetConformidadeContabilTest}), reproduzindo o MESMO limite
 * transacional que {@code RegistrarEmpenho.executar} usa em produção: cada
 * "thread de empenho" consulta o saldo (o que trava a linha da dotação) e só
 * então insere o empenho, tudo na mesma transação/conexão.
 */
@Testcontainers(disabledWithoutDocker = true)
class ExecucaoSaldoDotacaoConcorrenciaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static String enteId;
    private static String periodoId;
    private static String dotacaoId;

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
                    + "values ('44444444444444', 'Ente Concorrencia Execucao', 'municipio') returning id")) {
                rs.next();
                enteId = rs.getString(1);
            }
        }

        periodoId = criarPeriodo(enteId, 2026, 1);
        // 1.000,00 de credito disponivel: dois empenhos de 600,00 cada NAO cabem juntos.
        dotacaoId = criarDotacao(enteId, "1000.00");
    }

    @Test
    void doisEmpenhosConcorrentesSobreAMesmaDotacaoSaoSerializadosENaoComprometemAlemDoAutorizado() throws Exception {
        Dinheiro valorEmpenho1 = Dinheiro.de("600.00");
        Dinheiro valorEmpenho2 = Dinheiro.de("600.00"); // 600 + 600 = 1200 > 1000 autorizado

        CountDownLatch travaAdquirida = new CountDownLatch(1);
        CountDownLatch podeComitar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> empenho1 = executor.submit(() -> {
                comprometerDotacao(valorEmpenho1, 1L, travaAdquirida, podeComitar);
                return null;
            });

            assertThat(travaAdquirida.await(5, TimeUnit.SECONDS))
                    .as("thread 1 deve adquirir a trava (select ... for update) na linha da dotação")
                    .isTrue();

            Future<SaldoDotacao> empenho2 = executor.submit(() -> consultarSaldoBloqueante(dotacaoId));

            // Prova de concorrência REAL (não sorte de agendamento): thread 2 tenta o
            // mesmo "for update" na mesma linha e deve continuar bloqueada NO POSTGRES
            // enquanto thread 1 segura a trava sem comitar.
            assertThatThrownBy(() -> empenho2.get(500, TimeUnit.MILLISECONDS))
                    .as("thread 2 deve ficar bloqueada no banco enquanto thread 1 não comita")
                    .isInstanceOf(TimeoutException.class);

            podeComitar.countDown();
            empenho1.get(5, TimeUnit.SECONDS);

            SaldoDotacao saldoVistoPelaThread2 = empenho2.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> saldoVistoPelaThread2.exigirSaldoParaComprometer(valorEmpenho2))
                    .as("thread 2 já vê o empenho comitado pela thread 1 e rejeita corretamente "
                            + "o 2º empenho (600 + 600 > 1000 autorizado)")
                    .isInstanceOf(SaldoInsuficienteException.class)
                    .hasMessageContaining("saldo disponível");

            SaldoDotacao saldoFinal = lerSaldo(dotacaoId);
            assertThat(saldoFinal.valorComprometido())
                    .as("apenas o empenho da thread 1 foi de fato persistido — "
                            + "a trava impediu o duplo comprometimento da dotação")
                    .isEqualTo(valorEmpenho1);
        } finally {
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Infraestrutura do teste
    // ------------------------------------------------------------------

    private static void comprometerDotacao(
            Dinheiro valor, long numeroSeq, CountDownLatch travaAdquirida, CountDownLatch podeComitar)
            throws SQLException, InterruptedException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(conexao, true));
            PostgresSaldosExecucaoPort port = new PostgresSaldosExecucaoPort(jdbcTemplate);

            SaldoDotacao saldo = port.saldoDotacao(TenantId.de(enteId), DotacaoId.de(dotacaoId));
            travaAdquirida.countDown();

            saldo.exigirSaldoParaComprometer(valor);
            podeComitar.await(5, TimeUnit.SECONDS);

            // empenho.fato_contabil_id tem FK composta para fato_contabil(ente_id, id)
            // (RAZ-13) — precisa existir antes do insert do empenho, mesmo neste teste
            // que não exercita o motor do razão em si (fora do escopo desta trava).
            UUID fatoContabilId = UUID.randomUUID();
            jdbcTemplate.update(
                    "insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, "
                            + "tipo_evento, historico, origem) values (?,?,?,current_date,?,'empenho',?,?)",
                    fatoContabilId, UUID.fromString(enteId), numeroSeq, UUID.fromString(periodoId),
                    "empenho de teste de concorrencia", "teste-concorrencia");

            jdbcTemplate.update(
                    "insert into empenho (ente_id, numero_sequencial, exercicio, tipo, dotacao_id, credor_id, "
                            + "unidade_gestora_id, valor, data_fato, classificacao_orcamentaria, fonte_recurso, "
                            + "historico, fato_contabil_id) values (?,?,?,?,?,?,?,?,current_date,?,?,?,?)",
                    UUID.fromString(enteId), numeroSeq, 2026, "ordinario", UUID.fromString(dotacaoId),
                    UUID.randomUUID(), UUID.randomUUID(), valor.valor(), "teste-concorrencia", "teste-concorrencia",
                    "empenho de teste de concorrencia", fatoContabilId);

            conexao.commit();
        }
    }

    private static SaldoDotacao consultarSaldoBloqueante(String dotacaoIdParam) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(conexao, true));
            PostgresSaldosExecucaoPort port = new PostgresSaldosExecucaoPort(jdbcTemplate);
            SaldoDotacao saldo = port.saldoDotacao(TenantId.de(enteId), DotacaoId.de(dotacaoIdParam));
            conexao.commit();
            return saldo;
        }
    }

    private static SaldoDotacao lerSaldo(String dotacaoIdParam) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(conexao, true));
            SaldoDotacao saldo = new PostgresSaldosExecucaoPort(jdbcTemplate)
                    .saldoDotacao(TenantId.de(enteId), DotacaoId.de(dotacaoIdParam));
            conexao.commit();
            return saldo;
        }
    }

    private static void setEnteDaSessao(Connection conexao) throws SQLException {
        try (Statement st = conexao.createStatement()) {
            st.execute("set local app.ente_id = '" + enteId + "'");
        }
    }

    private static String criarPeriodo(String enteIdParam, int exercicio, int mes) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(
                            ("insert into periodo_contabil (ente_id, exercicio, mes, status) "
                                    + "values ('%s', %d, %d, 'aberto') returning id")
                                    .formatted(enteIdParam, exercicio, mes))) {
                rs.next();
                String id = rs.getString(1);
                conexao.commit();
                return id;
            }
        }
    }

    private static String criarDotacao(String enteIdParam, String valorAutorizado) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(("insert into dotacao (ente_id, exercicio, "
                            + "classificacao_orcamentaria, fonte_recurso, unidade_gestora_id, valor_autorizado) "
                            + "values ('%s', 2026, 'teste-concorrencia', 'teste-concorrencia', '%s', %s) "
                            + "returning id")
                            .formatted(enteIdParam, UUID.randomUUID(), valorAutorizado))) {
                rs.next();
                String id = rs.getString(1);
                conexao.commit();
                return id;
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection appLoginConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
    }
}
