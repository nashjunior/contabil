package br.contabil.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.auditoria.AuditoriaEscrita;
import br.contabil.plataforma.domain.auditoria.AuditoriaLeitura;
import br.contabil.plataforma.domain.auditoria.EventoAuditoria;
import br.contabil.plataforma.infra.auditoria.PostgresAuditoriaRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prova a entrega RAZ-6 no repo real: a trilha de auditoria e append-only,
 * hash-chain por ente, escrita por funcao do banco e isolada por RLS.
 */
@Testcontainers(disabledWithoutDocker = true)
class AuditoriaHashChainIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE_A = "11111111-1111-1111-1111-111111111111";
    private static final String ENTE_B = "22222222-2222-2222-2222-222222222222";

    @BeforeAll
    static void migraESemeiaEntes() throws SQLException {
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
        }
    }

    @Test
    void appendEncadeiaHashPorEnteComSequenciaContigua() {
        AuditoriaEscrita.EntradaTrilha primeiro = executarComoAppLogin(ENTE_A, repositorio -> repositorio.append(evento(
                ENTE_A, "fato_contabil_registrado", "operador-1", "fato:1", Map.of("base_legal", "OBRIGACAO_LEGAL"))));
        AuditoriaEscrita.EntradaTrilha segundo = executarComoAppLogin(ENTE_A, repositorio -> repositorio.append(evento(
                ENTE_A, "fato_contabil_estornado", "operador-1", "fato:2", Map.of("motivo", "erro_material"))));

        assertThat(primeiro.sequencia()).isPositive();
        assertThat(primeiro.hashEvento()).matches("[0-9a-f]{64}");
        assertThat(segundo.sequencia()).isEqualTo(primeiro.sequencia() + 1);
        assertThat(segundo.hashAnterior()).isEqualTo(primeiro.hashEvento());
        assertThat(segundo.hashEvento()).matches("[0-9a-f]{64}");
    }

    @Test
    void consultaRespeitaRlsDaSessao() {
        executarComoAppLogin(ENTE_A, repositorio -> repositorio.append(evento(
                ENTE_A, "acesso_dado_pessoal", "auditor-1", "cpf", Map.of("resultado", "permitido"))));

        var filtro = new AuditoriaLeitura.FiltroAuditoria(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));

        List<EventoAuditoria> eventosVisiveisParaA =
                executarComoAppLogin(ENTE_A, repositorio -> repositorio.consultar(filtro));
        List<EventoAuditoria> eventosVisiveisParaB =
                executarComoAppLogin(ENTE_B, repositorio -> repositorio.consultar(filtro));

        assertThat(eventosVisiveisParaA)
                .extracting(EventoAuditoria::ente)
                .containsOnly(TenantId.de(ENTE_A));
        assertThat(eventosVisiveisParaB).isEmpty();
    }

    @Test
    void rejeitaCpfClaroNaTrilha() {
        String cpfClaroSintetico = "111222333" + "44";

        assertThatThrownBy(() -> executarComoAppLogin(ENTE_A, repositorio -> repositorio.append(evento(
                ENTE_A, "acesso_dado_pessoal", "auditor-1", "cpf", Map.of("cpf", cpfClaroSintetico)))))
                .isInstanceOf(AuditoriaEscrita.FalhaAppendException.class);
    }

    @Test
    void appRoleNaoTemGrantParaAlterarOuApagarAuditoria() {
        executarComoAppLogin(ENTE_A, repositorio -> repositorio.append(evento(
                ENTE_A, "fato_contabil_registrado", "operador-1", "fato:imutavel", Map.of())));

        assertThatThrownBy(() -> executarSqlComoAppLogin(ENTE_A, "update auditoria_evento set tipo = 'tentativa'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> executarSqlComoAppLogin(ENTE_A, "delete from auditoria_evento"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    void triggerBloqueiaMutacaoMesmoParaDono() {
        executarComoAppLogin(ENTE_A, repositorio -> repositorio.append(evento(
                ENTE_A, "fato_contabil_registrado", "operador-1", "fato:trigger", Map.of())));

        assertThatThrownBy(() -> executarComoAdmin("update auditoria_evento set tipo = 'tentativa'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> executarComoAdmin("delete from auditoria_evento"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("append-only");
    }

    private static EventoAuditoria evento(
            String enteId, String tipo, String ator, String recurso, Map<String, String> detalhes) {
        return new EventoAuditoria(TenantId.de(enteId), tipo, ator, recurso, Instant.EPOCH, detalhes);
    }

    private static <T> T executarComoAppLogin(String enteId, AcaoRepositorio<T> acao) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), "app_login", "app_login");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        PostgresAuditoriaRepository repositorio = new PostgresAuditoriaRepository(jdbcTemplate);
        TransactionTemplate transacao = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        return transacao.execute(status -> {
            jdbcTemplate.execute("set local app.ente_id = '" + enteId + "'");
            return acao.executar(repositorio);
        });
    }

    private static void executarSqlComoAppLogin(String enteId, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login")) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("set local app.ente_id = '" + enteId + "'");
                st.execute(sql);
            }
            conn.commit();
        }
    }

    private static void executarComoAdmin(String sql) throws SQLException {
        try (Connection conn = adminConnection();
                Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @FunctionalInterface
    private interface AcaoRepositorio<T> {
        T executar(PostgresAuditoriaRepository repositorio);
    }
}
