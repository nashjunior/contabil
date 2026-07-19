package br.contabil.migration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guardrail de CI para RAZ-13: sem PK/FK composta por {@code ente_id}, o Postgres
 * checa a FK como DONO da tabela — ignora RLS — e um app_role consegue referenciar
 * período/fato/conta/conta-pai de OUTRO ente (ver ADR-0003, schema §Trava 4b).
 *
 * <p>Sobe um Postgres real via Testcontainers, aplica a migration V1 tal como em
 * produção e prova que toda referência cross-tenant é rejeitada por violação de
 * FK — sem depender de RLS silenciar a checagem em algum trigger.
 */
@Testcontainers(disabledWithoutDocker = true)
class RazaoRlsCrossTenantForeignKeyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE_A = "11111111-1111-1111-1111-111111111111";
    private static final String ENTE_B = "22222222-2222-2222-2222-222222222222";
    private static final String PERIODO_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String PERIODO_B = "aaaaaaaa-bbbb-bbbb-bbbb-aaaaaaaaaaaa";
    private static final String CONTA_A = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    private static final String CONTA_B = "cccccccc-dddd-dddd-dddd-cccccccccccc";
    private static final String FATO_A = "ffffffff-aaaa-aaaa-aaaa-ffffffffffff";
    private static final String FATO_B = "ffffffff-bbbb-bbbb-bbbb-ffffffffffff";

    @BeforeAll
    static void migraESemeiaDadosBase() throws SQLException {
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
                      ('%s', '11111111111111', 'Ente A', 'municipio'),
                      ('%s', '22222222222222', 'Ente B', 'municipio')
                    """.formatted(ENTE_A, ENTE_B));
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
                      ('%s', '%s', 1, current_date, '%s', 'empenho', 'fato semente ente A', 'test'),
                      ('%s', '%s', 1, current_date, '%s', 'empenho', 'fato semente ente B', 'test')
                    """.formatted(FATO_A, ENTE_A, PERIODO_A, FATO_B, ENTE_B, PERIODO_B));
        }
    }

    @Test
    void rejeitaFatoContabilComPeriodoDeOutroEnte() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_B, """
                insert into fato_contabil (ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem)
                values ('%s', 2, current_date, '%s', 'empenho', 'tentativa cross-tenant', 'test')
                """.formatted(ENTE_B, PERIODO_A)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void rejeitaFatoContabilComFatoEstornadoDeOutroEnte() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_B, """
                insert into fato_contabil (ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem, fato_estornado_id)
                values ('%s', 3, current_date, '%s', 'estorno', 'tentativa cross-tenant', 'test', '%s')
                """.formatted(ENTE_B, PERIODO_B, FATO_A)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void rejeitaContaPcaspComContaPaiDeOutroEnte() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_B, """
                insert into conta_pcasp (ente_id, codigo, descricao, natureza_informacao, natureza_saldo, conta_pai_id)
                values ('%s', '1.1', 'Sub', 'patrimonial', 'D', '%s')
                """.formatted(ENTE_B, CONTA_A)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void rejeitaLancamentoComFatoDeOutroEnte() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_B, """
                insert into lancamento (ente_id, fato_id, conta_id, natureza, valor)
                values ('%s', '%s', '%s', 'D', 100.00)
                """.formatted(ENTE_B, FATO_A, CONTA_B)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void rejeitaLancamentoComContaDeOutroEnte() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_B, """
                insert into lancamento (ente_id, fato_id, conta_id, natureza, valor)
                values ('%s', '%s', '%s', 'D', 100.00)
                """.formatted(ENTE_B, FATO_B, CONTA_A)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void permiteFatoContabilComPeriodoDoMesmoEnte() {
        assertThatCode(() -> executarComoAppLogin(ENTE_A, """
                insert into fato_contabil (ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem)
                values ('%s', 4, current_date, '%s', 'empenho', 'mesmo ente ok', 'test')
                """.formatted(ENTE_A, PERIODO_A)))
                .doesNotThrowAnyException();
    }

    @Test
    void permiteLancamentosBalanceadosNoMesmoEnte() {
        assertThatCode(() -> executarComoAppLogin(ENTE_A,
                "insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s', '%s', '%s', 'D', 50.00)"
                        .formatted(ENTE_A, FATO_A, CONTA_A),
                "insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s', '%s', '%s', 'C', 50.00)"
                        .formatted(ENTE_A, FATO_A, CONTA_A)))
                .doesNotThrowAnyException();
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void executarComoAppLogin(String enteId, String... sqls) throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login")) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("set local app.ente_id = '" + enteId + "'");
                for (String sql : sqls) {
                    st.execute(sql);
                }
            }
            conn.commit();
        }
    }
}
