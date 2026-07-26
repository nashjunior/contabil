package br.contabil.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guardrail de CI para RAZ-72 (reversibilidade de migração, arquitetura-tecnica §8): prova, num
 * Postgres real (Testcontainers) e com as migrations tal como em produção, que os scripts
 * {@code R{n}__..._down.sql} de {@code db/rollback} REALMENTE revertem cada {@code V{n}__...}
 * e que o ciclo up → down → up é idempotente (reconstrói um schema idêntico).
 *
 * <p>Os scripts {@code _down} ficam FORA de {@code db/migration} de propósito (Flyway Community
 * não roda undo migrations), então nada os exercitava — a promessa de reversibilidade da §8 era
 * só documental. Este teste fecha a lacuna: {@code migrate()} (Flyway), aplica os {@code _down}
 * em ordem reversa via JDBC e {@code migrate()} de novo, comparando a impressão digital do schema.
 *
 * <p>A paridade estrutural "todo V tem um R" (sem Docker) fica em {@link MigracaoRollbackParidadeTest}.
 */
@Testcontainers(disabledWithoutDocker = true)
class ReversibilidadeMigracaoFlywayTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    // Impressão digital canônica e determinística do schema public: tabelas+colunas, views,
    // funções (nome+assinatura), sequences, flags de RLS e políticas. Extensões (uuid-ossp,
    // pgcrypto), a role app_role e a flyway_schema_history sobrevivem ao rollback de propósito
    // e são excluídas para não introduzir ruído — o que importa é o schema RECONSTRUÍDO.
    private static final String SQL_IMPRESSAO_DIGITAL =
            """
            select string_agg(sig, chr(10) order by sig)
            from (
              select 'COL ' || table_name || '.' || column_name
                     || ' ' || data_type || ' null=' || is_nullable as sig
                from information_schema.columns
               where table_schema = 'public' and table_name <> 'flyway_schema_history'
              union all
              select 'VIEW ' || table_name
                from information_schema.views where table_schema = 'public'
              union all
              select 'FUNC ' || p.proname
                     || '(' || pg_get_function_identity_arguments(p.oid) || ')'
                from pg_proc p join pg_namespace n on n.oid = p.pronamespace
               where n.nspname = 'public'
              union all
              select 'SEQ ' || sequence_name
                from information_schema.sequences where sequence_schema = 'public'
              union all
              select 'RLS ' || c.relname
                     || ' enabled=' || c.relrowsecurity::text
                     || ' forced=' || c.relforcerowsecurity::text
                from pg_class c join pg_namespace n on n.oid = c.relnamespace
               where n.nspname = 'public' and c.relkind = 'r'
                 and c.relname <> 'flyway_schema_history'
              union all
              select 'POL ' || schemaname || '.' || tablename || '.' || policyname
                     || ' cmd=' || cmd || ' qual=' || coalesce(qual, '')
                from pg_policies where schemaname = 'public'
            ) s
            """;

    @Test
    void migracaoEReversivelEIdempotente() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load();

        // 1) UP — aplica todas as migrations versionadas e fotografa o schema.
        flyway.migrate();
        String impressaoAposPrimeiroUp;
        try (Connection conn = adminConnection()) {
            impressaoAposPrimeiroUp = impressaoDigitalDoSchema(conn);
            assertThat(tabelasDeUsuario(conn))
                    .as("a migração deve criar as tabelas do razão/execução/plataforma")
                    .contains("ente", "fato_contabil", "lancamento");
        }

        // 2) DOWN — rollback manual em ordem reversa (R4→R3→R2→R1): as tabelas de execução,
        //    auditoria e outbox referenciam ente por FK, então precisam cair antes da V1.
        try (Connection conn = adminConnection()) {
            aplicarRollbacksEmOrdemReversa(conn);
            assertThat(tabelasDeUsuario(conn))
                    .as("os scripts _down devem remover TODOS os objetos da migração "
                            + "(só extensões, app_role e flyway_schema_history sobrevivem)")
                    .isEmpty();
        }

        // 3) UP de novo — os scripts _down não conhecem a tabela interna do Flyway (correto: são
        //    SQL puro de rollback); quem 'esquece' o histórico para reaplicar é o operador/CI.
        try (Connection conn = adminConnection(); Statement st = conn.createStatement()) {
            st.execute("drop table if exists flyway_schema_history");
        }
        flyway.migrate();
        String impressaoAposSegundoUp;
        try (Connection conn = adminConnection()) {
            impressaoAposSegundoUp = impressaoDigitalDoSchema(conn);
        }

        assertThat(impressaoAposSegundoUp)
                .as("up→down→up deve reconstruir um schema idêntico — sem deriva de tabelas, "
                        + "colunas, funções, views, sequences, RLS ou políticas")
                .isEqualTo(impressaoAposPrimeiroUp);
    }

    // ------------------------------------------------------------------

    private static void aplicarRollbacksEmOrdemReversa(Connection conn) throws IOException, SQLException {
        for (Resource script : rollbacksEmOrdemReversa()) {
            String sql = new String(script.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            try (Statement st = conn.createStatement()) {
                st.execute(sql); // R-scripts são só DROPs simples; o driver PG roda o arquivo inteiro
            }
        }
    }

    private static List<Resource> rollbacksEmOrdemReversa() throws IOException {
        List<Resource> scripts = new ArrayList<>(List.of(
                new PathMatchingResourcePatternResolver().getResources("classpath*:db/rollback/R*.sql")));
        scripts.sort(Comparator.comparingInt(ReversibilidadeMigracaoFlywayTest::versaoDoScript).reversed());
        return scripts;
    }

    private static int versaoDoScript(Resource script) {
        Matcher m = Pattern.compile("^R(\\d+)__").matcher(String.valueOf(script.getFilename()));
        if (!m.find()) {
            throw new IllegalStateException("nome de script de rollback inesperado: " + script.getFilename());
        }
        return Integer.parseInt(m.group(1));
    }

    private static String impressaoDigitalDoSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(SQL_IMPRESSAO_DIGITAL)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static List<String> tabelasDeUsuario(Connection conn) throws SQLException {
        List<String> tabelas = new ArrayList<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "select table_name from information_schema.tables "
                                + "where table_schema = 'public' and table_type = 'BASE TABLE' "
                                + "and table_name <> 'flyway_schema_history' order by table_name")) {
            while (rs.next()) {
                tabelas.add(rs.getString(1));
            }
        }
        return tabelas;
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
