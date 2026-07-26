package br.contabil.escrita;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.contabil.RazaoApplication;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;

/**
 * RAZ-105: prova o ciclo F1 completo (empenhar → liquidar → aprovar → pagar)
 * através da borda HTTP real (controllers + beans do {@code ExecucaoConfiguracao}
 * + adapters de {@code execucao-infra}/roteiro contábil do {@code bootstrap}) —
 * mesmo padrão de {@code RegistrarEstornarFatoContabilIntegrationTest} (RAZ-27),
 * mas subindo o servidor de verdade ({@code webEnvironment = RANDOM_PORT}) em
 * vez de chamar os use cases diretamente, porque o que RAZ-105 fecha é
 * exatamente a lacuna de wiring HTTP/Spring — chamar os use cases direto não
 * provaria isso.
 *
 * <p>{@link ServicoIdentidade} é substituído por um duplo que autentica
 * qualquer {@code Authorization: Bearer <cpf>} como aquele CPF (RBAC sempre
 * concede) — o que está sob teste aqui é o wiring HTTP e as invariantes de
 * segregação de funções do próprio agregado (ADR-0023), não o RBAC (já coberto
 * por {@code AprovarPagamentoTest}/{@code RegistrarPagamentoTest}).
 */
@org.springframework.boot.test.context.SpringBootTest(
        classes = RazaoApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import(ExecucaoEscritaHttpIntegrationTest.ServicoIdentidadeDeTeste.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExecucaoEscritaHttpIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE = "88888888-8888-8888-8888-888888888888";
    private static final String CPF_ORDENADOR = "11111111111";
    private static final String CPF_APROVADOR = "22222222222";

    private static String dotacaoId;

    @LocalServerPort
    private int porta;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    /** Duplo de teste: autentica qualquer bearer como o CPF literal do token; RBAC sempre concede. */
    @TestConfiguration
    static class ServicoIdentidadeDeTeste {
        @Bean
        @Primary
        ServicoIdentidade servicoIdentidadeDeTeste() {
            return new ServicoIdentidade() {
                @Override
                public Sessao autenticar(Credencial credencial) {
                    String cpf = ((CredencialGovBr) credencial).assercao();
                    return new Sessao(
                            UUID.randomUUID(),
                            new Cpf(cpf),
                            new TenantId(UUID.fromString(ENTE)),
                            java.util.Optional.empty(),
                            true,
                            java.time.Instant.parse("2030-01-01T00:00:00Z"));
                }

                @Override
                public boolean autorizar(Sessao sessao, Recurso recurso, Acao acao) {
                    return true;
                }

                @Override
                public Sessao completarMfa(DesafioMfa desafio, RespostaMfa resposta) {
                    throw new UnsupportedOperationException("fake de teste: só autorizar()/autenticar() são usados");
                }
            };
        }
    }

    @BeforeAll
    static void migraESemeiaBase() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection admin = adminConnection();
                Statement st = admin.createStatement()) {
            st.execute("create role app_login login password 'app_login'");
            st.execute("grant app_role to app_login");
            st.execute(("insert into ente (id, cnpj, nome, esfera) values ('%s', '88888888888888', 'Ente RAZ-105', "
                            + "'municipio')")
                    .formatted(ENTE));
            st.execute(("insert into periodo_contabil (ente_id, exercicio, mes, status) "
                            + "values ('%s', 2026, 7, 'aberto')")
                    .formatted(ENTE));

            for (String[] conta : contasPcaspNecessarias()) {
                st.execute(("insert into conta_pcasp (ente_id, codigo, descricao, natureza_informacao, natureza_saldo) "
                                + "values ('%s', '%s', '%s', '%s', 'D')")
                        .formatted(ENTE, conta[0], conta[1], conta[2]));
            }

            try (ResultSet rs = st.executeQuery(("insert into dotacao (ente_id, exercicio, classificacao_orcamentaria, "
                            + "fonte_recurso, unidade_gestora_id, valor_autorizado) "
                            + "values ('%s', 2026, 'raz-105', 'raz-105', '%s', 100000.00) returning id")
                    .formatted(ENTE, UUID.randomUUID()))) {
                rs.next();
                dotacaoId = rs.getString(1);
            }
        }
    }

    private static String[][] contasPcaspNecessarias() {
        return new String[][] {
            {"6.2.2.1.1", "Credito Disponivel", "orcamentaria"},
            {"6.2.2.1.3", "Credito Empenhado a Liquidar", "orcamentaria"},
            {"6.2.2.1.4", "Empenhado Liquidado a Pagar", "orcamentaria"},
            {"6.2.2.1.5", "Empenhado Pago", "orcamentaria"},
            {"3.3.3.1.01", "VPD de servicos", "patrimonial"},
            {"2.1.3", "Fornecedores a Pagar", "patrimonial"},
            {"1.1.1", "Caixa e Bancos", "patrimonial"},
        };
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

    private static String empenhoId;
    private static String liquidacaoId;

    @Test
    @Order(1)
    void empenhaViaHttpReal() {
        Map<String, Object> corpo = Map.of(
                "dotacaoId", dotacaoId,
                "tipo", "ordinario",
                "credorId", UUID.randomUUID().toString(),
                "unidadeGestoraId", UUID.randomUUID().toString(),
                "valor", "12300.00",
                "dataFato", "2026-07-15",
                "exercicio", 2026,
                "classificacaoOrcamentaria", "raz-105",
                "fonteRecurso", "raz-105",
                "historico", "empenho via HTTP real (RAZ-105)");

        ResponseEntity<Map> resposta = post("/execucao/empenhos", CPF_ORDENADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).containsEntry("valor", "12300.00");
        assertThat(resposta.getBody().get("fatoContabilId")).isNotNull();
        empenhoId = (String) resposta.getBody().get("id");
    }

    @Test
    @Order(2)
    void liquidaViaHttpReal() {
        Map<String, Object> documento =
                Map.of("tipo", "nota_fiscal", "numero", "NF-105", "dataEmissao", "2026-07-14");
        Map<String, Object> corpo = Map.of(
                "empenhoId", empenhoId,
                "dataCompetencia", "2026-07-16",
                "valor", "4200.00",
                "documentosSuporte", List.of(documento),
                "historico", "liquidacao via HTTP real (RAZ-105)");

        ResponseEntity<Map> resposta = post("/execucao/liquidacoes", CPF_ORDENADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).containsEntry("status", "pendente");
        liquidacaoId = (String) resposta.getBody().get("id");
    }

    @Test
    @Order(3)
    void pagamentoAntesDaAprovacaoEhRejeitadoComPagamentoNaoAprovado() {
        ResponseEntity<Map> resposta = post("/execucao/pagamentos", CPF_APROVADOR, pagamentoValido(), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).containsEntry("codigo", "pagamento_nao_aprovado");
    }

    @Test
    @Order(4)
    void autoAprovacaoEhRejeitada() {
        Map<String, Object> corpo = Map.of("decisao", "aprovar");

        ResponseEntity<Map> resposta =
                post("/execucao/liquidacoes/" + liquidacaoId + "/aprovacao", CPF_ORDENADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).containsEntry("codigo", "auto_aprovacao_vedada");
    }

    @Test
    @Order(5)
    void aprovaComUmSegundoOrdenadorEEfetivaOPagamento() {
        Map<String, Object> aprovacao = Map.of("decisao", "aprovar");
        ResponseEntity<Map> respostaAprovacao =
                post("/execucao/liquidacoes/" + liquidacaoId + "/aprovacao", CPF_APROVADOR, aprovacao, Map.class);

        assertThat(respostaAprovacao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaAprovacao.getBody()).containsEntry("status", "aprovada");

        ResponseEntity<Map> respostaPagamento =
                post("/execucao/pagamentos", CPF_APROVADOR, pagamentoValido(), Map.class);

        assertThat(respostaPagamento.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respostaPagamento.getBody()).containsEntry("valor", "4200.00");
        assertThat(respostaPagamento.getBody().get("fatoContabilId")).isNotNull();
    }

    private static Map<String, Object> pagamentoValido() {
        Map<String, Object> beneficiario = Map.of("nome", "Fornecedor RAZ-105", "cpfCnpj", "12345678901");
        Map<String, Object> corpo = new java.util.HashMap<>();
        corpo.put("liquidacaoId", liquidacaoId);
        corpo.put("dataCompetencia", "2026-07-20");
        corpo.put("valor", "4200.00");
        corpo.put("natureza", "orcamentario");
        corpo.put("beneficiario", beneficiario);
        corpo.put("historico", "pagamento via HTTP real (RAZ-105)");
        return corpo;
    }

    private <T> ResponseEntity<T> post(String caminho, String cpfAtor, Object corpo, Class<T> tipoResposta) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + cpfAtor);
        return restTemplate.exchange(
                url(caminho), HttpMethod.POST, new HttpEntity<>(corpo, headers), tipoResposta);
    }

    private String url(String caminho) {
        return "http://localhost:%d/api/v1/entes/%s%s".formatted(porta, ENTE, caminho);
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
