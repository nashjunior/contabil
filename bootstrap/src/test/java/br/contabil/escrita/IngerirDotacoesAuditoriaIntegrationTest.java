package br.contabil.escrita;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.contabil.execucao.application.IngerirDotacoes;
import br.contabil.execucao.domain.CreditoAdicional;
import br.contabil.execucao.domain.Dotacao;
import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.TipoCreditoAdicional;
import br.contabil.execucao.domain.repository.DotacaoRepository;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ControleAcesso;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.plataforma.infra.auditoria.PostgresAuditoriaRepository;

/**
 * Regressão RAZ-219: IngerirDotacoes precisa anexar auditoria real sem violar
 * ck_auditoria_evento_sem_cpf_claro.
 */
@Testcontainers(disabledWithoutDocker = true)
class IngerirDotacoesAuditoriaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE_ID = "11111111-1111-1111-1111-111111111111";
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);

    @BeforeAll
    static void migraESemeiaEnte() throws SQLException {
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
                      ('%s', '11111111111111', 'Ente RAZ-219', 'municipio')
                    """.formatted(ENTE_ID));
        }
    }

    @Test
    void auditoriaRealMascaraCpfDoAtorEDoHistoricoAntesDoAppend() {
        DotacaoId dotacaoId = DotacaoId.novo();
        CreditoAdicional credito = new CreditoAdicional(
                dotacaoId,
                TipoCreditoAdicional.SUPLEMENTAR,
                Dinheiro.de("5000.00"),
                "decreto 123/2026 autorizado pelo CPF 12345678901");

        executarComoAppLogin(jdbcTemplate -> {
            IngerirDotacoes useCase = new IngerirDotacoes(
                    new ControleAcesso(servicoIdentidadePermitindoTudo()),
                    new DotacaoRepositoryFake(),
                    new PostgresAuditoriaRepository(jdbcTemplate),
                    RELOGIO);

            useCase.executar(sessao(), TenantId.de(ENTE_ID), List.of(), List.of(credito));

            return null;
        });

        executarComoAppLogin(jdbcTemplate -> {
            String ator = jdbcTemplate.queryForObject(
                    "select ator from auditoria_evento where tipo = 'execucao_dotacao_ingerida'",
                    String.class);
            String detalhes = jdbcTemplate.queryForObject(
                    "select detalhes::text from auditoria_evento where tipo = 'execucao_dotacao_ingerida'",
                    String.class);

            assertThat(ator).isEqualTo("***.456.***-**");
            assertThat(detalhes)
                    .contains("***.456.***-**")
                    .doesNotContain("12345678901");
            return null;
        });
    }

    private static ServicoIdentidade servicoIdentidadePermitindoTudo() {
        return new ServicoIdentidade() {
            @Override
            public Sessao autenticar(Credencial credencial) {
                return sessao();
            }

            @Override
            public boolean autorizar(Sessao sessao, Recurso recurso, Acao acao) {
                return true;
            }

            @Override
            public Sessao completarMfa(DesafioMfa desafio, RespostaMfa resposta) {
                return sessao();
            }
        };
    }

    private static Sessao sessao() {
        return new Sessao(
                UUID.randomUUID(),
                new Cpf("12345678901"),
                TenantId.de(ENTE_ID),
                Optional.empty(),
                true,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    private static <T> T executarComoAppLogin(AcaoJdbc<T> acao) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), "app_login", "app_login");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        TransactionTemplate transacao = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        return transacao.execute(status -> {
            jdbcTemplate.execute("set local app.ente_id = '" + ENTE_ID + "'");
            return acao.executar(jdbcTemplate);
        });
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @FunctionalInterface
    private interface AcaoJdbc<T> {
        T executar(JdbcTemplate jdbcTemplate);
    }

    private static final class DotacaoRepositoryFake implements DotacaoRepository {

        @Override
        public void inserir(Dotacao dotacao) {}

        @Override
        public Optional<Dotacao> buscarPorId(TenantId enteId, DotacaoId id) {
            return Optional.empty();
        }

        @Override
        public ResultadoLote<DotacaoId> inserirEmLote(List<Dotacao> dotacoes) {
            return new ResultadoLote<>(dotacoes.stream().map(Dotacao::id).toList(), List.of());
        }

        @Override
        public ResultadoLote<DotacaoId> aplicarCreditosEmLote(TenantId enteId, List<CreditoAdicional> creditos) {
            return new ResultadoLote<>(creditos.stream().map(CreditoAdicional::dotacaoId).toList(), List.of());
        }
    }
}
