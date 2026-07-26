package br.contabil;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
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
import br.contabil.razao.application.ConsultarContas;
import br.contabil.razao.application.ConsultarSaldo;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.ContaNaoEncontradaException;
import br.contabil.razao.domain.ContaResumo;
import br.contabil.razao.domain.PaginaContas;

/**
 * RAZ-117 / ADR-0030 §6: prova o catálogo PCASP e a validação de existência (gaps 1 e 2)
 * pelo pipeline REAL — {@link ConsultarContas}/{@link ConsultarSaldo} sob os advisors de
 * tenant/transação, datasource {@code app_login} de menor privilégio com RLS forçada. Cobre o
 * que os testes de unidade (mocks) não alcançam: o SQL da paginação por keyset {@code (codigo, id)},
 * a busca por prefixo de código × {@code descricao ilike}, o {@code existe} do gap 2 e o
 * isolamento por tenant da leitura de {@code conta_pcasp}.
 */
@SpringBootTest(classes = RazaoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(CatalogoContasIntegrationTest.ServicoIdentidadePermissivoParaTeste.class)
class CatalogoContasIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /** Só {@code autorizar()} importa aqui (sempre permissivo); o anti-BOLA de {@code ControleAcesso} segue real. */
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

    private static final String ENTE_A = "aa111111-1111-1111-1111-111111111111";
    private static final String ENTE_B = "bb222222-2222-2222-2222-222222222222";

    // Ente A: hierarquia 1 → 1.1 → {1.1.1, 1.1.2}, e 2 (raiz separada).
    private static final String C_A_1 = "a0000001-0000-0000-0000-000000000001";
    private static final String C_A_11 = "a0000001-1000-0000-0000-000000000011";
    private static final String C_A_111 = "a0000001-1100-0000-0000-000000000111";
    private static final String C_A_112 = "a0000001-1200-0000-0000-000000000112";
    private static final String C_A_2 = "a0000002-0000-0000-0000-000000000002";
    // Ente B: mesmo código 1.1.1 (unique é por ente) — serve ao teste de isolamento.
    private static final String C_B_111 = "b0000001-1100-0000-0000-000000000111";

    @Autowired
    private ConsultarContas consultarContas;

    @Autowired
    private ConsultarSaldo consultarSaldo;

    private static Sessao sessao(TenantId ente) {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), ente, Optional.empty(), true, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @BeforeAll
    static void migraSemeiaEntesEContas() throws SQLException {
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
            // Pais antes dos filhos (FK composta conta_pai_id → (ente_id, id)).
            inserirConta(st, C_A_1, ENTE_A, "1", "Ativo", "patrimonial", "D", false, null);
            inserirConta(st, C_A_11, ENTE_A, "1.1", "Caixa e equivalentes", "patrimonial", "D", false, C_A_1);
            inserirConta(st, C_A_111, ENTE_A, "1.1.1", "Caixa", "patrimonial", "D", true, C_A_11);
            inserirConta(st, C_A_112, ENTE_A, "1.1.2", "Bancos conta movimento", "patrimonial", "D", true, C_A_11);
            inserirConta(st, C_A_2, ENTE_A, "2", "Passivo", "patrimonial", "C", false, null);
            inserirConta(st, C_B_111, ENTE_B, "1.1.1", "Caixa do ente B", "patrimonial", "D", true, null);
        }
    }

    private static void inserirConta(
            Statement st,
            String id,
            String enteId,
            String codigo,
            String descricao,
            String naturezaInformacao,
            String naturezaSaldo,
            boolean escrituravel,
            String contaPaiId)
            throws SQLException {
        st.execute(
                """
                insert into conta_pcasp
                  (id, ente_id, codigo, descricao, natureza_informacao, natureza_saldo, escrituravel, conta_pai_id)
                values ('%s', '%s', '%s', '%s', '%s', '%s', %s, %s)
                """
                        .formatted(
                                id,
                                enteId,
                                codigo,
                                descricao,
                                naturezaInformacao,
                                naturezaSaldo,
                                escrituravel,
                                contaPaiId == null ? "null" : "'" + contaPaiId + "'"));
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
    void listaTodasAsContasDoEnteEmOrdemDeCodigoComItemCompleto() {
        TenantId enteA = TenantId.de(ENTE_A);

        PaginaContas pagina = consultarContas.executar(
                sessao(enteA), enteA, Optional.empty(), Optional.of(100), Optional.empty());

        assertThat(pagina.itens()).extracting(ContaResumo::codigo).containsExactly("1", "1.1", "1.1.1", "1.1.2", "2");
        assertThat(pagina.proximoCursor()).isEmpty();

        ContaResumo caixa = pagina.itens().stream().filter(c -> c.codigo().equals("1.1.1")).findFirst().orElseThrow();
        assertThat(caixa.id()).isEqualTo(new ContaContabilId(UUID.fromString(C_A_111)));
        assertThat(caixa.descricao()).isEqualTo("Caixa");
        assertThat(caixa.naturezaSaldo()).isEqualTo("D");
        assertThat(caixa.naturezaInformacao()).isEqualTo("patrimonial");
        assertThat(caixa.escrituravel()).isTrue();
        assertThat(caixa.contaPaiId()).isEqualTo(new ContaContabilId(UUID.fromString(C_A_11)));

        ContaResumo raiz = pagina.itens().stream().filter(c -> c.codigo().equals("1")).findFirst().orElseThrow();
        assertThat(raiz.contaPaiId()).as("conta raiz não tem pai").isNull();
    }

    @Test
    void buscaCasaPrefixoDeCodigoOuDescricaoIlike() {
        TenantId enteA = TenantId.de(ENTE_A);

        PaginaContas porCodigo = consultarContas.executar(
                sessao(enteA), enteA, Optional.of("1.1"), Optional.of(100), Optional.empty());
        assertThat(porCodigo.itens()).extracting(ContaResumo::codigo).containsExactly("1.1", "1.1.1", "1.1.2");

        // "banco" (minúsculo) casa "Bancos conta movimento" por ILIKE, e nada por prefixo de código.
        PaginaContas porDescricao = consultarContas.executar(
                sessao(enteA), enteA, Optional.of("banco"), Optional.of(100), Optional.empty());
        assertThat(porDescricao.itens()).extracting(ContaResumo::codigo).containsExactly("1.1.2");
    }

    @Test
    void paginaPorCursorOpacoSemRepetirNemPular() {
        TenantId enteA = TenantId.de(ENTE_A);

        PaginaContas p1 = consultarContas.executar(
                sessao(enteA), enteA, Optional.empty(), Optional.of(2), Optional.empty());
        assertThat(p1.itens()).extracting(ContaResumo::codigo).containsExactly("1", "1.1");
        assertThat(p1.proximoCursor()).isPresent();

        PaginaContas p2 = consultarContas.executar(
                sessao(enteA), enteA, Optional.empty(), Optional.of(2), p1.proximoCursor());
        assertThat(p2.itens()).extracting(ContaResumo::codigo).containsExactly("1.1.1", "1.1.2");
        assertThat(p2.proximoCursor()).isPresent();

        PaginaContas p3 = consultarContas.executar(
                sessao(enteA), enteA, Optional.empty(), Optional.of(2), p2.proximoCursor());
        assertThat(p3.itens()).extracting(ContaResumo::codigo).containsExactly("2");
        assertThat(p3.proximoCursor()).as("última página não tem próximo cursor").isEmpty();
    }

    @Test
    void catalogoIsoladoPorTenant() {
        TenantId enteA = TenantId.de(ENTE_A);

        // O ente A vê só as 5 contas dele; a conta 1.1.1 do ente B (mesmo código) nunca aparece.
        PaginaContas pagina = consultarContas.executar(
                sessao(enteA), enteA, Optional.empty(), Optional.of(100), Optional.empty());
        assertThat(pagina.itens()).extracting(ContaResumo::id).doesNotContain(new ContaContabilId(UUID.fromString(C_B_111)));
        assertThat(pagina.itens()).hasSize(5);
    }

    @Test
    void saldoDistingueContaInexistenteDeContaExistenteSemLancamento() {
        TenantId enteA = TenantId.de(ENTE_A);

        // gap 2: conta que não existe no ente → conta_nao_encontrada (nunca zero silencioso).
        ContaContabilId inexistente = ContaContabilId.novo();
        assertThatThrownBy(() -> consultarSaldo.executar(sessao(enteA), enteA, inexistente))
                .isInstanceOf(ContaNaoEncontradaException.class);

        // conta do ente B não existe para o ente A (RLS) → também conta_nao_encontrada.
        assertThatThrownBy(() ->
                        consultarSaldo.executar(sessao(enteA), enteA, new ContaContabilId(UUID.fromString(C_B_111))))
                .isInstanceOf(ContaNaoEncontradaException.class);

        // conta existente sem nenhum lançamento → zero legítimo, não erro.
        Dinheiro saldo = consultarSaldo.executar(sessao(enteA), enteA, new ContaContabilId(UUID.fromString(C_A_111)));
        assertThat(saldo).isEqualByComparingTo(Dinheiro.zero());
    }

    private static Connection adminConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
