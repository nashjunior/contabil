package br.contabil.golden;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.infra.PostgresDotacoesQuery;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/** RAZ-148: prova a listagem de dotações contra Postgres real, com saldo inline e cursor keyset. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresDotacoesQueryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static String enteId;
    private static String outroEnteId;
    private static String periodoId;

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
            enteId = criarEnte(st, "66666666666666", "Ente Query Dotacao");
            outroEnteId = criarEnte(st, "77777777777777", "Outro Ente Query Dotacao");
        }
        periodoId = criarPeriodo(enteId, 2026, 7);
        criarPeriodo(outroEnteId, 2026, 7);
    }

    @Test
    @DisplayName("lista por exercício com saldo derivado, busca por classificação e cursor keyset")
    void listaPorExercicioComSaldoDerivadoBuscaECursor() throws SQLException {
        String primeira = criarDotacao(enteId, 2026, "04.122.0001.2001", "1000.00");
        String segunda = criarDotacao(enteId, 2026, "04.122.0001.2002", "2000.00");
        criarDotacao(enteId, 2025, "04.122.0001.1999", "9999.00");
        criarDotacao(outroEnteId, 2026, "04.122.0001.2000", "9999.00");
        criarEmpenho(enteId, periodoId, primeira, "250.00", 1L);

        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, enteId);
            var query = new PostgresDotacoesQuery(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));

            var pagina1 = query.consultar(TenantId.de(enteId), 2026, Optional.empty(), 1, Optional.empty());
            assertThat(pagina1.itens()).hasSize(1);
            assertThat(pagina1.itens().get(0).id()).isEqualTo(DotacaoId.de(primeira));
            assertThat(pagina1.itens().get(0).valorComprometido()).isEqualTo(Dinheiro.de("250.00"));
            assertThat(pagina1.itens().get(0).saldoDisponivel()).isEqualTo(Dinheiro.de("750.00"));
            assertThat(pagina1.proximoCursor()).isPresent();

            var pagina2 = query.consultar(TenantId.de(enteId), 2026, Optional.empty(), 1, pagina1.proximoCursor());
            assertThat(pagina2.itens()).hasSize(1);
            assertThat(pagina2.itens().get(0).id()).isEqualTo(DotacaoId.de(segunda));
            assertThat(pagina2.itens().get(0).saldoDisponivel()).isEqualTo(Dinheiro.de("2000.00"));
            assertThat(pagina2.proximoCursor()).isEmpty();

            var busca = query.consultar(TenantId.de(enteId), 2026, Optional.of("04.122.0001.2002"), 20, Optional.empty());
            assertThat(busca.itens()).extracting(item -> item.id().valor().toString()).containsExactly(segunda);

            conexao.commit();
        }
    }

    private static String criarEnte(Statement st, String cnpj, String nome) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "insert into ente (cnpj, nome, esfera) values ('%s','%s','municipio') returning id"
                        .formatted(cnpj, nome))) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static String criarPeriodo(String ente, int exercicio, int mes) throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, ente);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(
                            "insert into periodo_contabil (ente_id, exercicio, mes, status) "
                                    + "values ('%s',%d,%d,'aberto') returning id"
                                            .formatted(ente, exercicio, mes))) {
                rs.next();
                conexao.commit();
                return rs.getString(1);
            }
        }
    }

    private static String criarDotacao(String ente, int exercicio, String classificacao, String valorAutorizado)
            throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, ente);
            try (Statement st = conexao.createStatement();
                    ResultSet rs = st.executeQuery(("insert into dotacao (ente_id, exercicio, "
                                    + "classificacao_orcamentaria, fonte_recurso, unidade_gestora_id, valor_autorizado) "
                                    + "values ('%s', %d, '%s', '0100000000', '%s', %s) returning id")
                            .formatted(ente, exercicio, classificacao, UUID.randomUUID(), valorAutorizado))) {
                rs.next();
                conexao.commit();
                return rs.getString(1);
            }
        }
    }

    private static void criarEmpenho(String ente, String periodo, String dotacao, String valor, long numeroSeq)
            throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao, ente);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(conexao, true));
            UUID fatoContabilId = UUID.randomUUID();
            jdbcTemplate.update(
                    "insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, "
                            + "tipo_evento, historico, origem) values (?,?,?,'2026-07-15',?,'empenho',?,?)",
                    fatoContabilId,
                    UUID.fromString(ente),
                    numeroSeq,
                    UUID.fromString(periodo),
                    "fato teste dotacoes query",
                    "teste-dotacoes-query");
            jdbcTemplate.update(
                    "insert into empenho (ente_id, numero_sequencial, exercicio, tipo, dotacao_id, credor_id, "
                            + "unidade_gestora_id, valor, data_fato, classificacao_orcamentaria, fonte_recurso, "
                            + "historico, fato_contabil_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    UUID.fromString(ente),
                    numeroSeq,
                    2026,
                    "ordinario",
                    UUID.fromString(dotacao),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    Dinheiro.de(valor).valor(),
                    java.sql.Date.valueOf("2026-07-15"),
                    "04.122.0001.2001",
                    "0100000000",
                    "empenho teste dotacoes query",
                    fatoContabilId);
            conexao.commit();
        }
    }

    private static void setEnteDaSessao(Connection conexao, String ente) throws SQLException {
        try (Statement st = conexao.createStatement()) {
            st.execute("set local app.ente_id = '" + ente + "'");
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection appLoginConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
    }
}
