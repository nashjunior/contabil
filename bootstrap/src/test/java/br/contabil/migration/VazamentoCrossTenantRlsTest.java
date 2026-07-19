package br.contabil.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guardrail de CI bloqueante (ADR-0003): prova, com um Postgres real e a migration V1 tal
 * como em produção, que RLS deny-by-default não vaza UMA LINHA SEQUER entre entes em
 * NENHUMA das 5 tabelas protegidas — abre transações com {@code app.ente_id} distinto por
 * ente e confirma zero linhas cruzadas, além do deny-by-default sem a variável setada. Ver
 * razao-contabil-schema.md §"Como o guardião testa isto", trava 4.
 *
 * <p>Complementa, sem substituir: {@code RazaoContabilTravasTest} cobre a trava 4 base só em
 * {@code fato_contabil}; {@code RazaoRlsCrossTenantForeignKeyTest} (RAZ-13) cobre a trava 4b
 * (bypass via FK simples no INSERT/DONO). Este teste cobre o lado SELECT — a superfície que
 * um vazamento cross-tenant reprovaria no controle externo (ADR-0003) — em toda tabela com
 * {@code ente_id}.
 */
@Testcontainers(disabledWithoutDocker = true)
class VazamentoCrossTenantRlsTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE_A = "33333333-3333-3333-3333-333333333333";
    private static final String ENTE_B = "44444444-4444-4444-4444-444444444444";
    private static final String PERIODO_A = "aaaaaaaa-3333-3333-3333-aaaaaaaaaaaa";
    private static final String PERIODO_B = "aaaaaaaa-4444-4444-4444-aaaaaaaaaaaa";
    private static final String CONTA_A = "cccccccc-3333-3333-3333-cccccccccccc";
    private static final String CONTA_B = "cccccccc-4444-4444-4444-cccccccccccc";
    private static final String FATO_A = "ffffffff-3333-3333-3333-ffffffffffff";
    private static final String FATO_B = "ffffffff-4444-4444-4444-ffffffffffff";

    @BeforeAll
    static void migraESemeiaDuasEntesComDadosEmTodasAsTabelasProtegidas() throws SQLException {
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
                      ('%s', '33333333333333', 'Ente A', 'municipio'),
                      ('%s', '44444444444444', 'Ente B', 'municipio')
                    """.formatted(ENTE_A, ENTE_B));
            // contador_fato ganha 1 linha por ente via trg_inicializa_contador_fato — nenhum
            // insert manual necessário para essa tabela ficar com massa de dado dos dois entes.
            st.execute("""
                    insert into periodo_contabil (id, ente_id, exercicio, mes, status) values
                      ('%s', '%s', 2026, 1, 'aberto'),
                      ('%s', '%s', 2026, 1, 'aberto')
                    """.formatted(PERIODO_A, ENTE_A, PERIODO_B, ENTE_B));
            st.execute("""
                    insert into conta_pcasp (id, ente_id, codigo, descricao, natureza_informacao, natureza_saldo) values
                      ('%s', '%s', '1', 'Ativo', 'patrimonial', 'D'),
                      ('%s', '%s', '1', 'Ativo', 'patrimonial', 'D')
                    """.formatted(CONTA_A, ENTE_A, CONTA_B, ENTE_B));
            st.execute("""
                    insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem) values
                      ('%s', '%s', 1, current_date, '%s', 'empenho', 'fato ente A', 'test'),
                      ('%s', '%s', 1, current_date, '%s', 'empenho', 'fato ente B', 'test')
                    """.formatted(FATO_A, ENTE_A, PERIODO_A, FATO_B, ENTE_B, PERIODO_B));
            st.execute("""
                    insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values
                      ('%s', '%s', '%s', 'D', 100.00),
                      ('%s', '%s', '%s', 'C', 100.00),
                      ('%s', '%s', '%s', 'D', 200.00),
                      ('%s', '%s', '%s', 'C', 200.00)
                    """.formatted(
                    ENTE_A, FATO_A, CONTA_A,
                    ENTE_A, FATO_A, CONTA_A,
                    ENTE_B, FATO_B, CONTA_B,
                    ENTE_B, FATO_B, CONTA_B));
        }
    }

    /** As 5 tabelas com {@code ente_id} sob RLS forçada (trava 4, migration V1). */
    static Stream<String> tabelasProtegidasPorRls() {
        return Stream.of("conta_pcasp", "periodo_contabil", "contador_fato", "fato_contabil", "lancamento");
    }

    @ParameterizedTest(name = "{0}: soma do que A e B enxergam bate com o total — zero linha cruzada")
    @MethodSource("tabelasProtegidasPorRls")
    void nenhumaLinhaCruzaEntreEntes(String tabela) throws SQLException {
        int totalNoBanco = contarComoAdmin(tabela);
        assertThat(totalNoBanco)
                .as("massa de teste: %s deveria ter pelo menos 1 linha por ente", tabela)
                .isGreaterThanOrEqualTo(2);

        int visivelParaA = contarComoAppLogin(tabela, ENTE_A);
        int visivelParaB = contarComoAppLogin(tabela, ENTE_B);

        // Se A enxergasse qualquer linha de B (ou vice-versa), a soma passaria do total real —
        // essa é a prova de "zero linha cruzada", não apenas "A não vê o fato específico de B".
        assertThat(visivelParaA + visivelParaB)
                .as("%s: soma do que A e B enxergam não pode exceder o total real (vazamento cross-tenant)", tabela)
                .isEqualTo(totalNoBanco);
        assertThat(visivelParaA).as("%s: ente A enxerga ao menos a própria linha", tabela).isGreaterThan(0);
        assertThat(visivelParaB).as("%s: ente B enxerga ao menos a própria linha", tabela).isGreaterThan(0);
    }

    @ParameterizedTest(name = "{0}: sem app.ente_id, RLS nega tudo (deny-by-default)")
    @MethodSource("tabelasProtegidasPorRls")
    void semAppEnteIdNaoRetornaLinhaNenhuma(String tabela) throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select count(*) from " + tabela)) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("%s: sem app.ente_id definido, deny-by-default deve zerar a consulta mesmo havendo linhas no banco", tabela)
                    .isZero();
        }
    }

    private static int contarComoAdmin(String tabela) throws SQLException {
        try (Connection conn = adminConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select count(*) from " + tabela)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int contarComoAppLogin(String tabela, String enteId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login")) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("set local app.ente_id = '" + enteId + "'");
                try (ResultSet rs = st.executeQuery("select count(*) from " + tabela)) {
                    rs.next();
                    int count = rs.getInt(1);
                    conn.commit();
                    return count;
                }
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
