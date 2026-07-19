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
 * Guardrail de CI para RAZ-3: prova, com um Postgres real (Testcontainers) e a
 * migration V1 tal como em produção, as travas do schema do razão que a
 * {@code RazaoRlsCrossTenantForeignKeyTest} (RAZ-13) não cobre — aquela cobre só
 * a trava 4b (FK composta). Ver tabela "Como o guardião testa isto" em
 * docs/arquitetura-tecnica/razao-contabil-schema.md:
 *
 * <ul>
 *   <li>Trava 1 — partidas dobradas (Σdébito = Σcrédito no commit)</li>
 *   <li>Trava 2 — imutabilidade (grant ausente + trigger, defesa em profundidade)</li>
 *   <li>Trava 3 — período encerrado</li>
 *   <li>Trava 4 — RLS deny-by-default (sem {@code app.ente_id} / ente errado)</li>
 *   <li>{@code numero_seq} único por ente e {@code valor} com check/escala</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
class RazaoContabilTravasTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE_A = "11111111-1111-1111-1111-111111111111";
    private static final String ENTE_B = "22222222-2222-2222-2222-222222222222";
    private static final String PERIODO_ABERTO = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String PERIODO_ENCERRADO = "aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa";
    private static final String CONTA_A = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    private static final String FATO_CONSOLIDADO = "ffffffff-cccc-cccc-cccc-ffffffffffff";

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
                      ('%s', '%s', 2026, 2, 'encerrado')
                    """.formatted(PERIODO_ABERTO, ENTE_A, PERIODO_ENCERRADO, ENTE_A));
            st.execute("""
                    insert into conta_pcasp (id, ente_id, codigo, descricao, natureza_informacao, natureza_saldo) values
                      ('%s', '%s', '1', 'Ativo', 'patrimonial', 'D')
                    """.formatted(CONTA_A, ENTE_A));
        }

        // Fato consolidado (partidas balanceadas, já commitado) — alvo dos testes de imutabilidade.
        executarComoAppLogin(ENTE_A,
                """
                insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem)
                values ('%s', '%s', 1, current_date, '%s', 'empenho', 'fato consolidado', 'test')
                """.formatted(FATO_CONSOLIDADO, ENTE_A, PERIODO_ABERTO),
                "insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s', '%s', '%s', 'D', 100.00)"
                        .formatted(ENTE_A, FATO_CONSOLIDADO, CONTA_A),
                "insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s', '%s', '%s', 'C', 100.00)"
                        .formatted(ENTE_A, FATO_CONSOLIDADO, CONTA_A));
    }

    // ------------------------------------------------------------------
    // Trava 1 — partidas dobradas (Σdébito = Σcrédito por fato, no commit)
    // ------------------------------------------------------------------

    @Test
    void rejeitaPartidasDesbalanceadasNoCommit() {
        String fatoId = "ffffffff-1111-1111-1111-ffffffffffff";
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_A,
                """
                insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem)
                values ('%s', '%s', 10, current_date, '%s', 'empenho', 'fato desbalanceado', 'test')
                """.formatted(fatoId, ENTE_A, PERIODO_ABERTO),
                "insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s', '%s', '%s', 'D', 100.00)"
                        .formatted(ENTE_A, fatoId, CONTA_A)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Partidas dobradas");
    }

    // ------------------------------------------------------------------
    // Trava 2 — imutabilidade (defesa em profundidade: grant + trigger)
    // ------------------------------------------------------------------

    @Test
    void appRoleNaoTemGrantParaAlterarFatoConsolidado() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_A,
                "update fato_contabil set historico = 'tentativa' where id = '%s'".formatted(FATO_CONSOLIDADO)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void appRoleNaoTemGrantParaApagarFatoConsolidado() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_A,
                "delete from fato_contabil where id = '%s'".formatted(FATO_CONSOLIDADO)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void appRoleNaoTemGrantParaAlterarLancamentoConsolidado() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_A,
                "update lancamento set valor = 1.00 where fato_id = '%s'".formatted(FATO_CONSOLIDADO)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void appRoleNaoTemGrantParaApagarLancamentoConsolidado() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_A,
                "delete from lancamento where fato_id = '%s'".formatted(FATO_CONSOLIDADO)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void triggerBloqueiaUpdateEmFatoConsolidadoMesmoParaDono() {
        // Conecta como dono/superuser (bypassa o grant) para provar a 2a camada: o
        // trigger de imutabilidade também rejeita, defesa em profundidade de verdade.
        assertThatThrownBy(() -> executarComoAdmin(
                "update fato_contabil set historico = 'tentativa' where id = '%s'".formatted(FATO_CONSOLIDADO)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("imutavel");
    }

    @Test
    void triggerBloqueiaDeleteEmFatoConsolidadoMesmoParaDono() {
        assertThatThrownBy(() -> executarComoAdmin(
                "delete from fato_contabil where id = '%s'".formatted(FATO_CONSOLIDADO)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("imutavel");
    }

    @Test
    void triggerBloqueiaUpdateEmLancamentoConsolidadoMesmoParaDono() {
        assertThatThrownBy(() -> executarComoAdmin(
                "update lancamento set valor = 1.00 where fato_id = '%s'".formatted(FATO_CONSOLIDADO)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("imutavel");
    }

    @Test
    void triggerBloqueiaDeleteEmLancamentoConsolidadoMesmoParaDono() {
        assertThatThrownBy(() -> executarComoAdmin(
                "delete from lancamento where fato_id = '%s'".formatted(FATO_CONSOLIDADO)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("imutavel");
    }

    // ------------------------------------------------------------------
    // Trava 3 — período encerrado (bloqueia registro retroativo)
    // ------------------------------------------------------------------

    @Test
    void rejeitaFatoContabilEmPeriodoEncerrado() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_A,
                """
                insert into fato_contabil (ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem)
                values ('%s', 11, current_date, '%s', 'empenho', 'tentativa em periodo encerrado', 'test')
                """.formatted(ENTE_A, PERIODO_ENCERRADO)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Periodo encerrado");
    }

    // ------------------------------------------------------------------
    // Trava 4 — RLS deny-by-default (base; a variante FK/4b está em RAZ-13)
    // ------------------------------------------------------------------

    @Test
    void consultaSemAppEnteIdNaoRetornaLinhas() throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select * from fato_contabil")) {
            assertThat(rs.next())
                    .as("sem app.ente_id definido, RLS deve negar tudo (deny-by-default)")
                    .isFalse();
        }
    }

    @Test
    void consultaComEnteIdDeOutroEnteNaoRetornaLinhasDoEnteA() throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login")) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("set local app.ente_id = '" + ENTE_B + "'");
                try (ResultSet rs = st.executeQuery(
                        "select * from fato_contabil where id = '" + FATO_CONSOLIDADO + "'")) {
                    assertThat(rs.next())
                            .as("fato do ente A não pode ser visível sob app.ente_id do ente B")
                            .isFalse();
                }
            }
            conn.commit();
        }
    }

    // ------------------------------------------------------------------
    // numero_seq único por ente + valor (check / escala da coluna)
    // ------------------------------------------------------------------

    @Test
    void rejeitaNumeroSeqDuplicadoParaMesmoEnte() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_A,
                """
                insert into fato_contabil (ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem)
                values ('%s', 1, current_date, '%s', 'empenho', 'numero_seq duplicado', 'test')
                """.formatted(ENTE_A, PERIODO_ABERTO)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("duplicate key");
    }

    @Test
    void rejeitaLancamentoComValorNegativo() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_A,
                "insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s', '%s', '%s', 'D', -50.00)"
                        .formatted(ENTE_A, FATO_CONSOLIDADO, CONTA_A)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("check constraint");
    }

    @Test
    void rejeitaLancamentoComValorZero() {
        assertThatThrownBy(() -> executarComoAppLogin(ENTE_A,
                "insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s', '%s', '%s', 'D', 0.00)"
                        .formatted(ENTE_A, FATO_CONSOLIDADO, CONTA_A)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("check constraint");
    }

    @Test
    void arredondaValorComMaisDeDuasCasasParaEscalaDaColuna() throws SQLException {
        // numeric(18,2) arredonda em vez de rejeitar valor com mais de 2 casas — quem
        // garante "nunca mais de 2 casas" é o tipo da coluna, não um check à parte.
        String fatoId = "ffffffff-2222-2222-2222-ffffffffffff";
        executarComoAppLogin(ENTE_A,
                """
                insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem)
                values ('%s', '%s', 12, current_date, '%s', 'empenho', 'valor com mais de 2 casas', 'test')
                """.formatted(fatoId, ENTE_A, PERIODO_ABERTO),
                "insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s', '%s', '%s', 'D', 10.129)"
                        .formatted(ENTE_A, fatoId, CONTA_A),
                "insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s', '%s', '%s', 'C', 10.13)"
                        .formatted(ENTE_A, fatoId, CONTA_A));

        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login")) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("set local app.ente_id = '" + ENTE_A + "'");
                try (ResultSet rs = st.executeQuery(
                        "select valor from lancamento where fato_id = '" + fatoId + "' and natureza = 'D'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBigDecimal("valor")).isEqualByComparingTo("10.13");
                }
            }
            conn.commit();
        }
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

    private static void executarComoAdmin(String... sqls) throws SQLException {
        try (Connection conn = adminConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                for (String sql : sqls) {
                    st.execute(sql);
                }
            }
            conn.commit();
        }
    }
}
