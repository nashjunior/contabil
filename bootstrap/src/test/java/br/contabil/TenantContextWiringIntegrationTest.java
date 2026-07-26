package br.contabil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.application.RegistrarFatoContabil;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.TipoEvento;

/**
 * Guardrail de CI para RAZ-21: prova, com um Postgres real e RLS forçada
 * (migration V1 tal como em produção), que o pipeline de ESCRITA do razão
 * funciona FIM-A-FIM através do wiring de produção — sobe o contexto Spring de
 * verdade ({@link RazaoApplication}) e chama {@link RegistrarFatoContabil#executar}
 * como bean gerenciado (advisors de {@code TransacaoUseCasesConfiguration} +
 * {@code TenantContextUseCasesConfiguration}), SEM nenhum {@code SET LOCAL}
 * manual via JDBC cru — diferente dos demais guardrails de {@code migration},
 * que testam o contrato do banco isoladamente. Datasource de runtime aponta
 * para {@code app_login} (o papel de menor privilégio), não para o superuser do
 * Testcontainers: superuser sempre ignora RLS, mesmo com {@code FORCE}, então só
 * assim a trava 4 realmente entra em jogo.
 */
@SpringBootTest(classes = RazaoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(TenantContextWiringIntegrationTest.ServicoIdentidadePermissivoParaTeste.class)
class TenantContextWiringIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /**
     * RAZ-33: em produção {@code ServicoIdentidadeIndisponivel} nega tudo
     * (RAZ-5 ainda não plugado). Este teste prova o pipeline de RLS/tenant
     * (RAZ-21), não RBAC/MFA — substitui o bean por um duplo permissivo, igual
     * a qualquer outro adapter de teste (Postgres real, identidade fake).
     */
    @TestConfiguration
    static class ServicoIdentidadePermissivoParaTeste {
        @Bean
        @Primary
        ServicoIdentidade servicoIdentidadePermissivoDeTeste() {
            return new ServicoIdentidade() {
                @Override
                public Sessao autenticar(Credencial credencial) {
                    throw new UnsupportedOperationException("fake de teste: só autorizar() é usado");
                }

                @Override
                public boolean autorizar(Sessao sessao, Recurso recurso, Acao acao) {
                    return true;
                }

                @Override
                public Sessao completarMfa(DesafioMfa desafio, RespostaMfa resposta) {
                    throw new UnsupportedOperationException("fake de teste: só autorizar() é usado");
                }
            };
        }
    }

    private static Sessao sessaoAutenticada(TenantId ente) {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), ente, Optional.empty(), true,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    private static final String ENTE_A = "77777777-7777-7777-7777-777777777777";
    private static final String ENTE_B = "88888888-8888-8888-8888-888888888888";
    private static final String PERIODO_A = "aaaaaaaa-7777-7777-7777-aaaaaaaaaaaa";
    private static final String PERIODO_B = "aaaaaaaa-8888-8888-8888-aaaaaaaaaaaa";
    private static final String CONTA_A = "cccccccc-7777-7777-7777-cccccccccccc";
    private static final String CONTA_B = "cccccccc-8888-8888-8888-cccccccccccc";

    @Autowired
    private RegistrarFatoContabil registrarFatoContabil;

    @BeforeAll
    static void migraCriaLoginDeMenorPrivilegioESemeiaDados() throws SQLException {
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
                      ('%s', '77777777777777', 'Ente A', 'municipio'),
                      ('%s', '88888888888888', 'Ente B', 'municipio')
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
        }
    }

    @DynamicPropertySource
    static void datasourceDeRuntimeUsaOLoginDeMenorPrivilegio(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_login");
        registry.add("spring.datasource.password", () -> "app_login");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("siafic.security.database.require-ssl", () -> "false");
    }

    @Test
    void registrarFatoContabilFuncionaFimAFimContraRlsForcadaSemSetLocalManual() {
        FatoContabil fato = registrarFatoContabil.executar(
                sessaoAutenticada(TenantId.de(ENTE_A)),
                TenantId.de(ENTE_A),
                LocalDate.of(2026, Month.JANUARY, 15),
                TipoEvento.EMPENHO,
                "fato via pipeline de produção (RAZ-21)",
                "test",
                List.of(
                        Lancamento.de(new ContaContabilId(UUID.fromString(CONTA_A)), Natureza.DEBITO, Dinheiro.de("100.00")),
                        Lancamento.de(new ContaContabilId(UUID.fromString(CONTA_A)), Natureza.CREDITO, Dinheiro.de("100.00"))));

        assertThat(fato.numeroSeq()).isEqualTo(1L);
        assertThat(fato.enteId()).isEqualTo(TenantId.de(ENTE_A));
    }

    @Test
    void numeracaoPorEnteFicaIsoladaMesmoPassandoPeloPipelineRealDaAplicacao() {
        FatoContabil fatoA = registrarFatoContabil.executar(
                sessaoAutenticada(TenantId.de(ENTE_A)),
                TenantId.de(ENTE_A),
                LocalDate.of(2026, Month.JANUARY, 16),
                TipoEvento.EMPENHO,
                "fato A",
                "test",
                List.of(
                        Lancamento.de(new ContaContabilId(UUID.fromString(CONTA_A)), Natureza.DEBITO, Dinheiro.de("10.00")),
                        Lancamento.de(new ContaContabilId(UUID.fromString(CONTA_A)), Natureza.CREDITO, Dinheiro.de("10.00"))));
        FatoContabil fatoB = registrarFatoContabil.executar(
                sessaoAutenticada(TenantId.de(ENTE_B)),
                TenantId.de(ENTE_B),
                LocalDate.of(2026, Month.JANUARY, 17),
                TipoEvento.EMPENHO,
                "fato B",
                "test",
                List.of(
                        Lancamento.de(new ContaContabilId(UUID.fromString(CONTA_B)), Natureza.DEBITO, Dinheiro.de("20.00")),
                        Lancamento.de(new ContaContabilId(UUID.fromString(CONTA_B)), Natureza.CREDITO, Dinheiro.de("20.00"))));

        assertThat(fatoA.numeroSeq())
                .as("contador_fato é isolado por app.ente_id — o pipeline real não pode vazar a sequência de A para B")
                .isNotEqualTo(fatoB.numeroSeq());
        assertThat(fatoB.numeroSeq()).isEqualTo(1L);
    }

    @Test
    void semGrantDiretoEmContadorFatoFalhaAltoEmVezDeVazarPermissao() {
        // contador_fato não tem grant select para app_role (só via proximo_numero_seq()) —
        // aqui provamos, pelo pipeline real, que o wiring de app.ente_id não contorna esse
        // grant: uma leitura direta (fora do produto) continua negada.
        assertThatThrownBy(() -> {
            try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login")) {
                conn.setAutoCommit(false);
                try (PreparedStatement setTenant = conn.prepareStatement("select set_config('app.ente_id', ?, true)")) {
                    setTenant.setString(1, ENTE_A);
                    setTenant.execute();
                }
                try (Statement st = conn.createStatement()) {
                    st.executeQuery("select * from contador_fato");
                }
            }
        }).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
