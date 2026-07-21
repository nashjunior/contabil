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

/**
 * Guardrail RAZ-15: Flyway usa a credencial dona de migration; runtime herda
 * app_role e fica sem CREATE/ALTER/DROP nem ownership de schema.
 */
@Testcontainers(disabledWithoutDocker = true)
class CredenciaisBancoSegregadasTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void migraECriaLoginRuntime() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection dono = adminConnection();
                Statement st = dono.createStatement()) {
            st.execute("create role app_login login password 'app_login'");
            st.execute("grant app_role to app_login");
        }
    }

    @Test
    void loginRuntimeNaoTemCreateNemOwnershipNoSchemaPublic() throws SQLException {
        try (Connection conn = runtimeConnection();
                Statement st = conn.createStatement()) {
            assertThat(privilegioSchema(st, "public", "CREATE")).isFalse();
            assertThat(privilegioSchema(st, "public", "USAGE")).isTrue();
            assertThat(ehDonoSchema(st, "public")).isFalse();
        }
    }

    @Test
    void loginRuntimeNaoCriaTabela() {
        assertThatThrownBy(() -> executarComoRuntime("create table tentativa_runtime_ddl (id int)"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void loginRuntimeNaoAlteraTabelaDoRazao() {
        assertThatThrownBy(() -> executarComoRuntime("alter table fato_contabil add column tentativa_runtime_ddl int"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("must be owner");
    }

    @Test
    void loginRuntimeNaoDerrubaTabelaDoRazao() {
        assertThatThrownBy(() -> executarComoRuntime("drop table fato_contabil"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("must be owner");
    }

    private static boolean privilegioSchema(Statement st, String schema, String privilegio) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "select has_schema_privilege('app_login', '%s', '%s')".formatted(schema, privilegio))) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private static boolean ehDonoSchema(Statement st, String schema) throws SQLException {
        try (ResultSet rs = st.executeQuery("""
                select pg_get_userbyid(nspowner) = current_user
                  from pg_namespace
                 where nspname = '%s'
                """.formatted(schema))) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private static void executarComoRuntime(String sql) throws SQLException {
        try (Connection conn = runtimeConnection();
                Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection runtimeConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
    }
}
