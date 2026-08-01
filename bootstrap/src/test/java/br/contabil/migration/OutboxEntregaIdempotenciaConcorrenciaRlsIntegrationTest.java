package br.contabil.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.contabil.plataforma.domain.ChaveIdempotencia;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.IdEntrega;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.MensagemEntrega;
import br.contabil.plataforma.domain.entrega.ServicoEntrega.StatusEntrega;
import br.contabil.plataforma.infra.entrega.PostgresServicoEntrega;

/**
 * RAZ-200: fecha o buraco de cobertura do outbox de entrega (ADR-0004/0011) que
 * {@code OutboxEntregaMigrationTest} não cobre — aquele exercita as funções SQL
 * SECURITY DEFINER em sequência (sem concorrência real) e nunca passa pelo adapter
 * Java. Este teste (1) chama {@link PostgresServicoEntrega} de verdade (não SQL cru)
 * para provar o enqueue idempotente e a RLS do {@code status()}; (2) prova com DUAS
 * transações reais que {@code outbox_entrega_reclamar} (FOR UPDATE SKIP LOCKED) não
 * entrega a mesma mensagem duas vezes sob concorrência.
 */
@Testcontainers(disabledWithoutDocker = true)
class OutboxEntregaIdempotenciaConcorrenciaRlsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static String enteAId;
    private static String enteBId;

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
                    + "values ('77777777777777', 'Ente Outbox A', 'municipio') returning id")) {
                rs.next();
                enteAId = rs.getString(1);
            }
            try (ResultSet rs = st.executeQuery("insert into ente (cnpj, nome, esfera) "
                    + "values ('88888888888888', 'Ente Outbox B', 'municipio') returning id")) {
                rs.next();
                enteBId = rs.getString(1);
            }
        }
    }

    @Test
    @DisplayName("enqueue com a mesma ChaveIdempotencia não cria segunda linha — devolve o mesmo IdEntrega")
    void enqueueEhIdempotentePelaChave() throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, enteAId);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(conexao, true));
            PostgresServicoEntrega servico = new PostgresServicoEntrega(jdbcTemplate);

            ChaveIdempotencia chave = ChaveIdempotencia.de("transparencia:execucao:empenho:idempotencia-teste");
            MensagemEntrega mensagem =
                    new MensagemEntrega(TenantId.de(enteAId), "transparencia", "execucao.empenho.registrado.v1", "{}");

            IdEntrega primeiro = servico.enqueue(mensagem, chave);
            IdEntrega segundo = servico.enqueue(mensagem, chave);

            assertThat(segundo)
                    .as("reenvio da mesma chave devolve o MESMO id de entrega, sem reenfileirar")
                    .isEqualTo(primeiro);
            conexao.commit();
        }

        assertThat(contarLinhasComoAdmin(enteAId, "transparencia:execucao:empenho:idempotencia-teste"))
                .as("idempotência real do banco (unique ente_id+chave, ON CONFLICT DO NOTHING) — nunca 2 linhas")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a mesma chave em entes diferentes NÃO colide — idempotência é por (ente_id, chave), não global")
    void idempotenciaEhEscopadaPorEnteNaoGlobal() throws SQLException {
        ChaveIdempotencia chave = ChaveIdempotencia.de("transparencia:execucao:empenho:mesma-chave-dois-entes");

        IdEntrega idEnteA = enqueueComo(enteAId, chave);
        IdEntrega idEnteB = enqueueComo(enteBId, chave);

        assertThat(idEnteA)
                .as("mesma chave, ente diferente => entrega DIFERENTE, não é a mesma linha reaproveitada")
                .isNotEqualTo(idEnteB);
    }

    @Test
    @DisplayName("status() nunca vaza mensagem de outro ente — RLS no SELECT direto (fora do worker security definer)")
    void statusRespeitaRlsPorEnte() throws SQLException {
        ChaveIdempotencia chave = ChaveIdempotencia.de("transparencia:execucao:empenho:rls-status-teste");
        IdEntrega id = enqueueComo(enteAId, chave);

        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, enteAId);
            StatusEntrega status =
                    new PostgresServicoEntrega(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)))
                            .status(id);
            assertThat(status).isEqualTo(StatusEntrega.ENFILEIRADO);
            conexao.commit();
        }

        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, enteBId);
            PostgresServicoEntrega servicoComoOutroEnte =
                    new PostgresServicoEntrega(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));
            assertThatThrownBy(() -> servicoComoOutroEnte.status(id))
                    .as("sessão de outro ente não enxerga a linha sob RLS — zero linhas, não vazamento")
                    .isInstanceOf(IllegalArgumentException.class);
            conexao.commit();
        }
    }

    @Test
    @DisplayName("claim concorrente real (SKIP LOCKED): duas transações disputando reclamar() — só uma entrega, a outra não duplica")
    void claimConcorrenteNaoEntregaDuplicada() throws Exception {
        // Testes anteriores desta classe deixam suas próprias mensagens 'enfileirado'
        // pendentes (nunca confirmadas) na mesma tabela — outbox_entrega_reclamar(10, ...)
        // as varre junto. Por isso a prova de concorrência checa a MENSAGEM ALVO por id
        // (contém/não-contém), não a contagem total reclamada por cada thread.
        String chaveClaim = "transparencia:execucao:empenho:claim-concorrente-teste";
        IdEntrega alvo = enqueueComo(enteAId, ChaveIdempotencia.de(chaveClaim));
        String idAlvo = alvo.valor().toString();

        CountDownLatch thread1Reclamou = new CountDownLatch(1);
        CountDownLatch podeComitar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<String>> reclamadasThread1 = executor.submit(
                    () -> reclamarSegurandoTransacao(thread1Reclamou, podeComitar));

            assertThat(thread1Reclamou.await(5, TimeUnit.SECONDS))
                    .as("thread 1 deve reclamar a mensagem (FOR UPDATE) antes da thread 2 tentar")
                    .isTrue();

            // Thread 2 tenta reclamar SEM esperar thread 1 comitar — a linha alvo está
            // bloqueada (FOR UPDATE) e outbox_entrega_reclamar usa SKIP LOCKED, então
            // thread 2 pode até reclamar OUTRAS mensagens pendentes (de testes
            // anteriores), mas nunca a mesma linha que a thread 1 já travou.
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

    private static IdEntrega enqueueComo(String enteId, ChaveIdempotencia chave) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, enteId);
            IdEntrega id = new PostgresServicoEntrega(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)))
                    .enqueue(
                            new MensagemEntrega(
                                    TenantId.de(enteId), "transparencia", "execucao.empenho.registrado.v1", "{}"),
                            chave);
            conexao.commit();
            return id;
        }
    }

    private static List<String> reclamarSegurandoTransacao(CountDownLatch reclamou, CountDownLatch podeComitar)
            throws SQLException, InterruptedException {
        try (Connection conexao = adminConnection()) {
            conexao.setAutoCommit(false);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(
                            "select id from outbox_entrega_reclamar(10, clock_timestamp() + interval '2 minutes')")) {
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
                    ResultSet rs = st.executeQuery(
                            "select id from outbox_entrega_reclamar(10, clock_timestamp() + interval '2 minutes')")) {
                List<String> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getString("id"));
                }
                conexao.commit();
                return ids;
            }
        }
    }

    private static int contarLinhasComoAdmin(String enteId, String chave) throws SQLException {
        try (Connection conexao = adminConnection();
                Statement st = conexao.createStatement();
                ResultSet rs = st.executeQuery(
                        "select count(*) from outbox_mensagem where ente_id = '" + enteId + "' and chave = '"
                                + chave + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void setEnteDaSessao(Connection conexao, String enteId) throws SQLException {
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
}
