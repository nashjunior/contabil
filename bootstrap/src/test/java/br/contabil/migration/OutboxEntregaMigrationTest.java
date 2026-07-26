package br.contabil.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class OutboxEntregaMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE_A = "55555555-5555-5555-5555-555555555555";
    private static final String ENTE_B = "66666666-6666-6666-6666-666666666666";
    private static final String OUTBOX_A = "aaaaaaaa-5555-5555-5555-aaaaaaaaaaaa";
    private static final String OUTBOX_B = "aaaaaaaa-6666-6666-6666-aaaaaaaaaaaa";

    @BeforeAll
    static void migraESemeiaOutbox() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection dono = adminConnection();
                Statement st = dono.createStatement()) {
            st.execute("create role app_login login password 'app_login'");
            st.execute("grant app_role to app_login");
            st.execute("""
                    insert into ente (id, cnpj, nome, esfera) values
                      ('%s', '55555555555555', 'Ente A', 'municipio'),
                      ('%s', '66666666666666', 'Ente B', 'municipio')
                    """.formatted(ENTE_A, ENTE_B));
            st.execute("""
                    insert into outbox_mensagem (id, ente_id, chave, destino, tipo, conteudo) values
                      ('%s', '%s', 'chave-a', 'transparencia', 'execucao.empenho.registrado.v1', '{"id":"a"}'),
                      ('%s', '%s', 'chave-b', 'transparencia', 'execucao.empenho.registrado.v1', '{"id":"b"}')
                    """.formatted(OUTBOX_A, ENTE_A, OUTBOX_B, ENTE_B));
        }
    }

    @Test
    void workerConsegueReclamarConfirmarRetentarEEnviarDlqSemAbrirSelectCrossTenant() throws SQLException {
        List<String> reclamadas = reclamarComoAppLogin();
        assertThat(reclamadas).containsExactly(OUTBOX_A, OUTBOX_B);

        executarComoAppLogin("select outbox_entrega_confirmar('%s')".formatted(OUTBOX_A));
        assertThat(statusComoAdmin(OUTBOX_A)).isEqualTo("entregue");

        executarComoAppLogin("""
                select outbox_entrega_retentativa('%s', clock_timestamp() + interval '5 seconds', 'timeout broker')
                """.formatted(OUTBOX_B));
        assertThat(statusComoAdmin(OUTBOX_B)).isEqualTo("retentando");
        assertThat(tentativasComoAdmin(OUTBOX_B)).isEqualTo(1);

        executarComoAppLogin("select outbox_entrega_dlq('%s', 'payload invalido')".formatted(OUTBOX_B));
        assertThat(statusComoAdmin(OUTBOX_B)).isEqualTo("falha_permanente");
        assertThat(contarDlqComoAdmin(OUTBOX_B)).isEqualTo(1);
    }

    private static List<String> reclamarComoAppLogin() throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("""
                        select id
                          from outbox_entrega_reclamar(10, clock_timestamp() + interval '2 minutes')
                         order by chave
                        """)) {
            List<String> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
            return ids;
        }
    }

    private static void executarComoAppLogin(String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
                Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private static String statusComoAdmin(String id) throws SQLException {
        try (Connection conn = adminConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select status from outbox_mensagem where id = '" + id + "'")) {
            rs.next();
            return rs.getString("status");
        }
    }

    private static int tentativasComoAdmin(String id) throws SQLException {
        try (Connection conn = adminConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select tentativas from outbox_mensagem where id = '" + id + "'")) {
            rs.next();
            return rs.getInt("tentativas");
        }
    }

    private static int contarDlqComoAdmin(String mensagemId) throws SQLException {
        try (Connection conn = adminConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select count(*) from outbox_dlq where mensagem_id = '" + mensagemId + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
