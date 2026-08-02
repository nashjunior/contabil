package br.contabil.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class TransparenciaPublicacaoMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE_A = "77777777-7777-7777-7777-777777777777";
    private static final String ENTE_B = "88888888-8888-8888-8888-888888888888";

    @BeforeAll
    static void migraECriaRoles() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection dono = adminConnection();
                Statement st = dono.createStatement()) {
            st.execute("create role app_login login password 'app_login'");
            st.execute("grant app_role to app_login");
            st.execute("create role transparencia_login login password 'transparencia_login'");
            st.execute("grant transparencia_publico_role to transparencia_login");
            st.execute("""
                    insert into ente (id, cnpj, nome, esfera) values
                      ('%s', '77777777777777', 'Ente A', 'municipio'),
                      ('%s', '88888888888888', 'Ente B', 'municipio')
                    """.formatted(ENTE_A, ENTE_B));
        }
    }

    @Test
    void rolePublicaESelectOnlyNoReadModelENaoLeTransacional() throws SQLException {
        try (Connection conn = publicConnection();
                Statement st = conn.createStatement()) {
            assertThat(privilegioTabela(st, "transparencia_login", "transparencia_publicacao", "SELECT")).isTrue();
            assertThat(privilegioTabela(st, "transparencia_login", "transparencia_publicacao", "INSERT")).isFalse();
            assertThat(privilegioTabela(st, "transparencia_login", "pagamento", "SELECT")).isFalse();
        }

        assertThatThrownBy(() -> executarComoPublico("select count(*) from pagamento"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> executarComoPublico("""
                insert into transparencia_publicacao
                  (ente_id, tipo_evento, recurso, publicar_ate, payload_json, chave_idempotencia)
                values
                  ('%s', 'execucao.pagamento.registrado.v1', 'execucao:pagamento:x', clock_timestamp(),
                   '{"estagio":"pago"}', 'publico-nao-insere')
                """.formatted(ENTE_A)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void rlsPublicaRecortaPorEnteDoPathViaAppEnteId() throws SQLException {
        inserirComoApp(ENTE_A, "chave-a", "execucao:pagamento:a");
        inserirComoApp(ENTE_B, "chave-b", "execucao:pagamento:b");

        try (Connection conn = publicConnection();
                Statement st = conn.createStatement()) {
            st.execute("select set_config('app.ente_id', '%s', false)".formatted(ENTE_A));
            try (ResultSet rs = st.executeQuery("select recurso from transparencia_publicacao order by recurso")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("recurso")).isEqualTo("execucao:pagamento:a");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    void bancoRejeitaPublicacaoForaDoSla() {
        assertThatThrownBy(() -> executarComoApp("""
                select transparencia_publicacao_inserir(
                  '%s',
                  'execucao.pagamento.registrado.v1',
                  'execucao:pagamento:late',
                  '2026-08-04T00:00:00Z',
                  '2026-08-03T23:59:59Z',
                  '{"estagio":"pago","pagamentoId":"late","valor":"1.00","publicarAte":"2026-08-03T23:59:59Z"}'::jsonb,
                  'chave-late')
                """.formatted(ENTE_A)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("transparencia_publicacao_check");
    }

    private static void inserirComoApp(String enteId, String chave, String recurso) throws SQLException {
        executarComoApp("""
                select transparencia_publicacao_inserir(
                  '%s',
                  'execucao.pagamento.registrado.v1',
                  '%s',
                  '2026-08-02T12:00:00Z',
                  '2026-08-03T23:59:59Z',
                  '{"estagio":"pago","pagamentoId":"%s","valor":"10.00","publicarAte":"2026-08-03T23:59:59Z"}'::jsonb,
                  '%s')
                """.formatted(enteId, recurso, recurso.substring(recurso.lastIndexOf(':') + 1), chave));
    }

    private static boolean privilegioTabela(Statement st, String role, String tabela, String privilegio) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "select has_table_privilege('%s', '%s', '%s')".formatted(role, tabela, privilegio))) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private static void executarComoApp(String sql) throws SQLException {
        try (Connection conn = appConnection();
                Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private static void executarComoPublico(String sql) throws SQLException {
        try (Connection conn = publicConnection();
                Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
    }

    private static Connection publicConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "transparencia_login", "transparencia_login");
    }
}
