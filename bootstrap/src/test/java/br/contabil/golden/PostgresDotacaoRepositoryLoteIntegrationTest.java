package br.contabil.golden;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

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

import br.contabil.execucao.domain.CreditoAdicional;
import br.contabil.execucao.domain.Dotacao;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.TipoCreditoAdicional;
import br.contabil.execucao.domain.UnidadeGestoraId;
import br.contabil.execucao.domain.repository.DotacaoRepository.ResultadoLote;
import br.contabil.execucao.infra.PostgresDotacaoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/**
 * RAZ-89: prova contra Postgres real a ingestão em lote fail-soft (ADR-0013) da dotação —
 * Fixação (carga da LOA) e créditos adicionais (Lei 4.320/1964 arts. 40-46).
 *
 * <p>Cobre dois pontos que só um Postgres de verdade confirma: (1) o {@code UPDATE} de
 * {@code valor_autorizado} sob o grant da V3 (RAZ-66, concedido para o {@code select ... for
 * update} do lock de saldo — RAZ-89 reaproveita o mesmo grant, sem nova migration); (2) o
 * {@code ON CONFLICT DO NOTHING} do insert em lote absorvendo reentrega idempotente sem
 * derrubar o restante do lote.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresDotacaoRepositoryLoteIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static String enteId;

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
                    + "values ('55555555555555', 'Ente Lote Dotacao', 'municipio') returning id")) {
                rs.next();
                enteId = rs.getString(1);
            }
        }
    }

    @Test
    @DisplayName("insere lote de fixações e ignora fail-soft a reentrega idempotente (ON CONFLICT DO NOTHING)")
    void insereLoteEIgnoraReentregaIdempotente() throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(conexao, true));
            PostgresDotacaoRepository repositorio = new PostgresDotacaoRepository(jdbcTemplate);

            Dotacao d1 = novaDotacao("04.122.0001.2001");
            Dotacao d2 = novaDotacao("04.122.0001.2002");

            ResultadoLote<DotacaoId> primeiraTentativa = repositorio.inserirEmLote(List.of(d1, d2));
            assertThat(primeiraTentativa.processados()).containsExactlyInAnyOrder(d1.id(), d2.id());
            assertThat(primeiraTentativa.erros()).isEmpty();

            ResultadoLote<DotacaoId> reentrega = repositorio.inserirEmLote(List.of(d1, d2));
            assertThat(reentrega.processados()).isEmpty();
            assertThat(reentrega.erros())
                    .as("reentrega idempotente do mesmo id não derruba o lote — aparece em erros")
                    .hasSize(2);

            conexao.commit();
        }
    }

    @Test
    @DisplayName("aplica crédito adicional atomicamente e é fail-soft para dotação inexistente")
    void aplicaCreditoAtomicoEFailSoftParaDotacaoInexistente() throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            setEnteDaSessao(conexao);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(conexao, true));
            PostgresDotacaoRepository repositorio = new PostgresDotacaoRepository(jdbcTemplate);

            Dotacao dotacao = novaDotacao("04.122.0001.2003");
            repositorio.inserir(dotacao);

            CreditoAdicional creditoValido = new CreditoAdicional(
                    dotacao.id(), TipoCreditoAdicional.SUPLEMENTAR, Dinheiro.de("500.00"), "decreto teste 1/2026");
            CreditoAdicional creditoParaDotacaoInexistente = new CreditoAdicional(
                    DotacaoId.novo(), TipoCreditoAdicional.ESPECIAL, Dinheiro.de("300.00"), "decreto teste 2/2026");

            ResultadoLote<DotacaoId> resultado = repositorio.aplicarCreditosEmLote(
                    TenantId.de(enteId), List.of(creditoValido, creditoParaDotacaoInexistente));

            assertThat(resultado.processados()).containsExactly(dotacao.id());
            assertThat(resultado.erros())
                    .as("crédito para dotação inexistente não derruba o crédito válido do mesmo lote")
                    .hasSize(1);

            Dotacao atualizada =
                    repositorio.buscarPorId(TenantId.de(enteId), dotacao.id()).orElseThrow();
            assertThat(atualizada.valorAutorizado())
                    .as("valor_autorizado somado atomicamente pelo UPDATE, sem select prévio")
                    .isEqualTo(Dinheiro.de("1500.00"));

            conexao.commit();
        }
    }

    private static Dotacao novaDotacao(String classificacaoOrcamentaria) {
        return Dotacao.registrar(
                DotacaoId.novo(),
                TenantId.de(enteId),
                2026,
                classificacaoOrcamentaria,
                "0100000000",
                UnidadeGestoraId.novo(),
                Dinheiro.de("1000.00"));
    }

    private static void setEnteDaSessao(Connection conexao) throws SQLException {
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
