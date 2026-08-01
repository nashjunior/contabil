package br.contabil.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RAZ-200: {@code execucao_empenho_documento_outbox} (V7/ADR-0027) não tinha NENHUM
 * teste — nem das funções SECURITY DEFINER (reclamar/confirmar/retentativa/dlq), nem
 * do enqueue idempotente de {@code PostgresSolicitacaoDocumentoAssinaturaPort}, nem da
 * RLS. Mesmo padrão de {@code OutboxEntregaMigrationTest}/{@code
 * OutboxEntregaIdempotenciaConcorrenciaRlsIntegrationTest} (SQL direto contra
 * Postgres real via Testcontainers): {@code PostgresEmpenhoDocumentoOutboxRepository}
 * e {@code PostgresSolicitacaoDocumentoAssinaturaPort} são package-private em
 * {@code execucao-infra} (não visíveis daqui), então este teste exercita a MESMA
 * superfície SQL que eles chamam — prova o comportamento real do banco, não uma
 * suposição sobre o adapter.
 */
@Testcontainers(disabledWithoutDocker = true)
class EmpenhoDocumentoOutboxIdempotenciaConcorrenciaRlsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static String enteAId;
    private static String enteBId;
    private static String periodoId;
    private static String dotacaoId;
    private static long proximoNumeroSequencial = 1;

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
                    + "values ('99999999999999', 'Ente Doc Outbox A', 'municipio') returning id")) {
                rs.next();
                enteAId = rs.getString(1);
            }
            try (ResultSet rs = st.executeQuery("insert into ente (cnpj, nome, esfera) "
                    + "values ('11111111199999', 'Ente Doc Outbox B', 'municipio') returning id")) {
                rs.next();
                enteBId = rs.getString(1);
            }
        }

        periodoId = criarPeriodo(enteAId, 2026, 1);
        dotacaoId = criarDotacao(enteAId, "1000000.00");
    }

    @Test
    @DisplayName("enqueue (insert...on conflict do nothing) é idempotente por (ente_id, empenho_id) — sem 2ª linha")
    void enqueueEhIdempotentePorEmpenho() throws SQLException {
        String empenhoId = criarEmpenho(enteAId);

        enfileirarDocumentoComoAppLogin(enteAId, empenhoId);
        enfileirarDocumentoComoAppLogin(enteAId, empenhoId);

        assertThat(contarLinhasComoAdmin(enteAId, empenhoId))
                .as("unique (ente_id, empenho_id) + ON CONFLICT DO NOTHING — reenfileirar não duplica")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("transições reais: pendente -> concluido e pendente -> retentando -> falha_permanente (DLQ)")
    void transicoesDeEstadoViaFuncoesSecurityDefiner() throws SQLException {
        String empenhoConcluido = criarEmpenho(enteAId);
        String empenhoFalha = criarEmpenho(enteAId);
        enfileirarDocumentoComoAppLogin(enteAId, empenhoConcluido);
        enfileirarDocumentoComoAppLogin(enteAId, empenhoFalha);

        List<String> reclamadas = reclamarComoAppLogin();
        assertThat(reclamadas).contains(idDoOutbox(empenhoConcluido), idDoOutbox(empenhoFalha));

        String idConcluido = idDoOutbox(empenhoConcluido);
        String idFalha = idDoOutbox(empenhoFalha);

        executarComoAppLogin("select execucao_empenho_documento_outbox_confirmar('%s')".formatted(idConcluido));
        assertThat(statusComoAdmin(idConcluido)).isEqualTo("concluido");

        executarComoAppLogin(("select execucao_empenho_documento_outbox_retentativa("
                        + "'%s', clock_timestamp() + interval '5 seconds', 'worker indisponível')")
                .formatted(idFalha));
        assertThat(statusComoAdmin(idFalha)).isEqualTo("retentando");
        assertThat(tentativasComoAdmin(idFalha)).isEqualTo(1);

        executarComoAppLogin(
                "select execucao_empenho_documento_outbox_dlq('%s', 'geração de PDF falhou')".formatted(idFalha));
        assertThat(statusComoAdmin(idFalha)).isEqualTo("falha_permanente");
    }

    @Test
    @DisplayName("SELECT direto (fora do worker security definer) nunca vaza outbox de outro ente — RLS")
    void selectDiretoRespeitaRlsPorEnte() throws SQLException {
        String empenhoId = criarEmpenho(enteAId);
        enfileirarDocumentoComoAppLogin(enteAId, empenhoId);

        assertThat(contarViaSelectComoAppLogin(enteAId))
                .as("com app.ente_id correto, a linha do próprio ente é visível")
                .isGreaterThanOrEqualTo(1);
        assertThat(contarViaSelectComoAppLogin(enteBId))
                .as("com app.ente_id de outro ente, zero linhas — deny-by-default, não vazamento")
                .isZero();
    }

    @Test
    @DisplayName("claim concorrente real (SKIP LOCKED): duas transações disputando reclamar() não duplicam a mesma linha")
    void claimConcorrenteNaoEntregaDuplicada() throws Exception {
        String empenhoId = criarEmpenho(enteAId);
        enfileirarDocumentoComoAppLogin(enteAId, empenhoId);
        String idAlvo = idDoOutbox(empenhoId);

        CountDownLatch thread1Reclamou = new CountDownLatch(1);
        CountDownLatch podeComitar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<String>> reclamadasThread1 = executor.submit(
                    () -> reclamarSegurandoTransacao(thread1Reclamou, podeComitar));

            assertThat(thread1Reclamou.await(5, TimeUnit.SECONDS))
                    .as("thread 1 deve reclamar antes da thread 2 tentar")
                    .isTrue();

            List<String> reclamadasThread2 = reclamarSemBloquear();
            assertThat(reclamadasThread2)
                    .as("SKIP LOCKED: thread 2 não reclama a linha alvo já travada pela thread 1")
                    .doesNotContain(idAlvo);

            podeComitar.countDown();
            assertThat(reclamadasThread1.get(5, TimeUnit.SECONDS))
                    .as("thread 1 reclamou a mensagem alvo")
                    .contains(idAlvo);
        } finally {
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Infraestrutura do teste
    // ------------------------------------------------------------------

    private static void enfileirarDocumentoComoAppLogin(String enteId, String empenhoId) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, enteId);
            try (Statement st = conexao.createStatement()) {
                st.execute(("insert into execucao_empenho_documento_outbox (ente_id, empenho_id) "
                                + "values ('%s', '%s') on conflict (ente_id, empenho_id) do nothing")
                        .formatted(enteId, empenhoId));
            }
            conexao.commit();
        }
    }

    private static String idDoOutbox(String empenhoId) throws SQLException {
        try (Connection conexao = adminConnection();
                Statement st = conexao.createStatement();
                ResultSet rs = st.executeQuery(
                        "select id from execucao_empenho_documento_outbox where empenho_id = '" + empenhoId + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static List<String> reclamarComoAppLogin() throws SQLException {
        try (Connection conn = appLoginConnection();
                Statement st = conn.createStatement()) {
            conn.setAutoCommit(false);
            try (ResultSet rs = st.executeQuery("select id from execucao_empenho_documento_outbox_reclamar("
                    + "10, clock_timestamp() + interval '2 minutes')")) {
                List<String> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getString("id"));
                }
                conn.commit();
                return ids;
            }
        }
    }

    private static List<String> reclamarSegurandoTransacao(CountDownLatch reclamou, CountDownLatch podeComitar)
            throws SQLException, InterruptedException {
        try (Connection conexao = adminConnection()) {
            conexao.setAutoCommit(false);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery("select id from execucao_empenho_documento_outbox_reclamar("
                            + "10, clock_timestamp() + interval '2 minutes')")) {
                List<String> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getString("id"));
                }
                reclamou.countDown();
                podeComitar.await(5, TimeUnit.SECONDS);
                conexao.commit();
                return ids;
            }
        }
    }

    private static List<String> reclamarSemBloquear() throws SQLException {
        try (Connection conexao = adminConnection()) {
            conexao.setAutoCommit(false);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery("select id from execucao_empenho_documento_outbox_reclamar("
                            + "10, clock_timestamp() + interval '2 minutes')")) {
                List<String> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getString("id"));
                }
                conexao.commit();
                return ids;
            }
        }
    }

    private static void executarComoAppLogin(String sql) throws SQLException {
        try (Connection conn = appLoginConnection();
                Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private static int contarViaSelectComoAppLogin(String enteIdDaSessao) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, enteIdDaSessao);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery("select count(*) from execucao_empenho_documento_outbox")) {
                rs.next();
                int total = rs.getInt(1);
                conexao.commit();
                return total;
            }
        }
    }

    private static int contarLinhasComoAdmin(String enteId, String empenhoId) throws SQLException {
        try (Connection conexao = adminConnection();
                Statement st = conexao.createStatement();
                ResultSet rs = st.executeQuery(
                        "select count(*) from execucao_empenho_documento_outbox where ente_id = '" + enteId
                                + "' and empenho_id = '" + empenhoId + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String statusComoAdmin(String id) throws SQLException {
        try (Connection conexao = adminConnection();
                Statement st = conexao.createStatement();
                ResultSet rs = st.executeQuery(
                        "select status from execucao_empenho_documento_outbox where id = '" + id + "'")) {
            rs.next();
            return rs.getString("status");
        }
    }

    private static int tentativasComoAdmin(String id) throws SQLException {
        try (Connection conexao = adminConnection();
                Statement st = conexao.createStatement();
                ResultSet rs = st.executeQuery(
                        "select tentativas from execucao_empenho_documento_outbox where id = '" + id + "'")) {
            rs.next();
            return rs.getInt("tentativas");
        }
    }

    private static synchronized String criarEmpenho(String enteId) throws SQLException {
        long numeroSeq = proximoNumeroSequencial++;
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, enteId);

            UUID fatoContabilId = UUID.randomUUID();
            try (var ps = conexao.prepareStatement(
                    "insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, "
                            + "tipo_evento, historico, origem) values (?,?,?,current_date,?,'empenho',?,?)")) {
                ps.setObject(1, fatoContabilId);
                ps.setObject(2, UUID.fromString(enteId));
                ps.setLong(3, numeroSeq);
                ps.setObject(4, UUID.fromString(periodoId));
                ps.setString(5, "empenho de teste do outbox de documento");
                ps.setString(6, "teste-doc-outbox");
                ps.executeUpdate();
            }

            String empenhoId;
            try (var ps = conexao.prepareStatement(
                    "insert into empenho (ente_id, numero_sequencial, exercicio, tipo, dotacao_id, credor_id, "
                            + "unidade_gestora_id, valor, data_fato, classificacao_orcamentaria, fonte_recurso, "
                            + "historico, fato_contabil_id) values (?,?,?,?,?,?,?,?,current_date,?,?,?,?) "
                            + "returning id")) {
                ps.setObject(1, UUID.fromString(enteId));
                ps.setLong(2, numeroSeq);
                ps.setInt(3, 2026);
                ps.setString(4, "ordinario");
                ps.setObject(5, UUID.fromString(dotacaoId));
                ps.setObject(6, UUID.randomUUID());
                ps.setObject(7, UUID.randomUUID());
                ps.setBigDecimal(8, new java.math.BigDecimal("100.00"));
                ps.setString(9, "teste-doc-outbox");
                ps.setString(10, "teste-doc-outbox");
                ps.setString(11, "empenho de teste do outbox de documento");
                ps.setObject(12, fatoContabilId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    empenhoId = rs.getString(1);
                }
            }
            conexao.commit();
            return empenhoId;
        }
    }

    private static void setEnteDaSessao(Connection conexao, String enteId) throws SQLException {
        try (Statement st = conexao.createStatement()) {
            st.execute("set local app.ente_id = '" + enteId + "'");
        }
    }

    private static String criarPeriodo(String enteId, int exercicio, int mes) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, enteId);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(
                            ("insert into periodo_contabil (ente_id, exercicio, mes, status) "
                                    + "values ('%s', %d, %d, 'aberto') returning id")
                                    .formatted(enteId, exercicio, mes))) {
                rs.next();
                String id = rs.getString(1);
                conexao.commit();
                return id;
            }
        }
    }

    private static String criarDotacao(String enteId, String valorAutorizado) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, enteId);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(("insert into dotacao (ente_id, exercicio, "
                            + "classificacao_orcamentaria, fonte_recurso, unidade_gestora_id, valor_autorizado) "
                            + "values ('%s', 2026, 'teste-doc-outbox', 'teste-doc-outbox', '%s', %s) "
                            + "returning id")
                            .formatted(enteId, UUID.randomUUID(), valorAutorizado))) {
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
