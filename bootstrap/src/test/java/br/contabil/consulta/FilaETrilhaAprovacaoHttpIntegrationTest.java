package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * RAZ-115 / ADR-0029: prova o contrato de LEITURA do gate pela borda HTTP real —
 * (A) fila de aprovação com a segregação da Regra 9 imposta no SERVIDOR (autor não
 * vê o próprio item; terceiro vê), (B) trilha dedicada montada sobre {@code
 * AuditoriaLeitura}, (C) dupla decisão → {@code 409 liquidacao_ja_decidida}. Mesmo
 * arcabouço de {@code ExecucaoEscritaHttpIntegrationTest} (Testcontainers + servidor
 * real + {@link ServicoIdentidade} falso que autentica o bearer como o CPF literal e
 * concede RBAC) — o que está sob teste é o read model + a segregação, não o RBAC.
 */
@org.springframework.boot.test.context.SpringBootTest(
        classes = RazaoApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import(FilaETrilhaAprovacaoHttpIntegrationTest.ServicoIdentidadeDeTeste.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FilaETrilhaAprovacaoHttpIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE = "77777777-7777-7777-7777-777777777777";
    private static final String CPF_ORDENADOR = "11111111111";
    private static final String CPF_APROVADOR = "22222222222";

    private static String dotacaoId;
    private static String empenhoId;
    private static String liquidacaoId;

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
            st.execute(("insert into ente (id, cnpj, nome, esfera) values ('%s', '77777777777777', 'Ente RAZ-115', "
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
                            + "values ('%s', 2026, 'raz-115', 'raz-115', '%s', 100000.00) returning id")
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

    @Test
    @Order(1)
    void empenhaELiquidaComoOrdenador() {
        Map<String, Object> empenho = Map.of(
                "dotacaoId", dotacaoId,
                "tipo", "ordinario",
                "credorId", UUID.randomUUID().toString(),
                "unidadeGestoraId", UUID.randomUUID().toString(),
                "valor", "12300.00",
                "dataFato", "2026-07-15",
                "exercicio", 2026,
                "classificacaoOrcamentaria", "raz-115",
                "fonteRecurso", "raz-115",
                "historico", "empenho leitura do gate (RAZ-115)");
        ResponseEntity<Map> respEmpenho = post("/execucao/empenhos", CPF_ORDENADOR, empenho, Map.class);
        assertThat(respEmpenho.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        empenhoId = (String) respEmpenho.getBody().get("id");

        Map<String, Object> documento =
                Map.of("tipo", "nota_fiscal", "numero", "NF-115", "dataEmissao", "2026-07-14");
        Map<String, Object> liquidacao = Map.of(
                "empenhoId", empenhoId,
                "dataCompetencia", "2026-07-16",
                "valor", "4200.00",
                "documentosSuporte", List.of(documento),
                "historico", "liquidacao leitura do gate (RAZ-115)");
        ResponseEntity<Map> respLiquidacao = post("/execucao/liquidacoes", CPF_ORDENADOR, liquidacao, Map.class);
        assertThat(respLiquidacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        liquidacaoId = (String) respLiquidacao.getBody().get("id");
    }

    @Test
    @Order(2)
    void filaComoTerceiroVeOItemPendenteComDinheiroStringESemPiiNominal() {
        ResponseEntity<Map> resposta =
                get("/execucao/liquidacoes?statusAprovacao=pendente", CPF_APROVADOR, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itens = (List<Map<String, Object>>) resposta.getBody().get("itens");
        Map<String, Object> item = itens.stream()
                .filter(i -> liquidacaoId.equals(i.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("terceiro deveria ver a liquidação pendente"));
        assertThat(item).containsEntry("valor", "4200.00");
        assertThat(item).containsEntry("statusAprovacao", "pendente");
        assertThat(item.get("credorId")).isNotNull();
        // resumo leve não vaza identidade nominal do credor (04-lgpd)
        assertThat(item).doesNotContainKeys("credorNome", "credorCpfCnpj", "beneficiario");
    }

    @Test
    @Order(3)
    void filaComoAutorNaoVeOProprioItem_segregacaoRegra9NoServidor() {
        ResponseEntity<Map> resposta =
                get("/execucao/liquidacoes?statusAprovacao=pendente", CPF_ORDENADOR, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itens = (List<Map<String, Object>>) resposta.getBody().get("itens");
        assertThat(itens).noneSatisfy(i -> assertThat(i.get("id")).isEqualTo(liquidacaoId));
    }

    @Test
    @Order(4)
    void trilhaTrazEmpenhoRegistradoELiquidacaoRegistradaComAtorMascarado() {
        ResponseEntity<Map> resposta =
                get("/execucao/liquidacoes/" + liquidacaoId + "/trilha", CPF_APROVADOR, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsEntry("liquidacaoId", liquidacaoId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> eventos = (List<Map<String, Object>>) resposta.getBody().get("eventos");
        assertThat(eventos)
                .extracting(e -> (String) e.get("tipo"))
                .containsSubsequence("execucao_empenho_registrado", "execucao_liquidacao_registrada");
        assertThat(eventos).allSatisfy(e -> assertThat((String) e.get("ator")).contains("*"));
    }

    @Test
    @Order(5)
    void segundaDecisaoSobreAMesmaLiquidacaoRetorna409LiquidacaoJaDecidida() {
        Map<String, Object> aprovar = Map.of("decisao", "aprovar");

        ResponseEntity<Map> primeira =
                post("/execucao/liquidacoes/" + liquidacaoId + "/aprovacao", CPF_APROVADOR, aprovar, Map.class);
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(primeira.getBody()).containsEntry("status", "aprovada");

        ResponseEntity<Map> segunda =
                post("/execucao/liquidacoes/" + liquidacaoId + "/aprovacao", CPF_APROVADOR, aprovar, Map.class);
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // O código do erro sai no envelope de erro da borda (RAZ-79 §6.1); afirmamos pelo VALOR e
        // não pela chave para o teste ficar agnóstico à convergência do envelope em curso (RAZ-116).
        assertThat(segunda.getBody()).containsValue("liquidacao_ja_decidida");
    }

    @Test
    @Order(6)
    void trilhaPassaAIncluirADecisaoAposAprovacao() {
        ResponseEntity<Map> resposta =
                get("/execucao/liquidacoes/" + liquidacaoId + "/trilha", CPF_APROVADOR, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> eventos = (List<Map<String, Object>>) resposta.getBody().get("eventos");
        assertThat(eventos)
                .extracting(e -> (String) e.get("tipo"))
                .contains("execucao_pagamento_aprovacao_decidida");
    }

    private <T> ResponseEntity<T> post(String caminho, String cpfAtor, Object corpo, Class<T> tipoResposta) {
        return restTemplate.exchange(
                url(caminho), HttpMethod.POST, new HttpEntity<>(corpo, headers(cpfAtor)), tipoResposta);
    }

    private <T> ResponseEntity<T> get(String caminho, String cpfAtor, Class<T> tipoResposta) {
        return restTemplate.exchange(
                url(caminho), HttpMethod.GET, new HttpEntity<>(null, headers(cpfAtor)), tipoResposta);
    }

    private static HttpHeaders headers(String cpfAtor) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + cpfAtor);
        return headers;
    }

    private String url(String caminho) {
        return "http://localhost:%d/api/v1/entes/%s%s".formatted(porta, ENTE, caminho);
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
