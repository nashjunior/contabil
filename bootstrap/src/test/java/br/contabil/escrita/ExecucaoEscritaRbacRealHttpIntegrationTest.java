package br.contabil.escrita;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import br.contabil.RazaoApplication;

/**
 * RAZ-218: prova o ciclo F1 completo (dotação → empenho → liquidação → aprovação → pagamento)
 * através da borda HTTP real contra a **matriz RBAC de verdade** — {@code siafic.iam.enabled=true},
 * JWT gov.br RS256 assinado neste teste (sem substituir {@link
 * br.contabil.plataforma.domain.iam.ServicoIdentidade} por duplo permissivo, diferente de {@link
 * ExecucaoEscritaHttpIntegrationTest}), com três CPFs/papéis segregados (ADR-0052):
 * {@code ADMIN_PLATAFORMA} fixa a dotação, {@code LANCADOR} empenha e liquida, {@code PAGADOR}
 * aprova e paga.
 *
 * <p>Fecha a lacuna descrita no RAZ-218: o teste HTTP existente falsificava {@code autorizar →
 * true} e semeava a dotação via SQL direto (nunca chamava o endpoint) — a matriz real de {@link
 * br.contabil.plataforma.infra.iam.IamProperties.Papel#permite} nunca era exercida contra os
 * endpoints. Este teste chama {@code POST .../dotacoes:lote} de verdade e autentica cada estágio
 * com uma asserção gov.br assinada, verificada pelo adapter real ({@code ServicoIdentidadeGovBrIcp}).
 */
@org.springframework.boot.test.context.SpringBootTest(
        classes = RazaoApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExecucaoEscritaRbacRealHttpIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ISSUER = "https://sso.staging.acesso.gov.br";
    private static final String AUDIENCE = "siafic-razao";

    private static final String ENTE = "99999999-9999-9999-9999-999999999999";
    private static final String CPF_PLANEJADOR = "33333333333";
    private static final String CPF_LANCADOR = "44444444444";
    private static final String CPF_PAGADOR = "55555555555";

    private static KeyPair keyPair;
    private static String dotacaoId;
    private static String empenhoId;
    private static String liquidacaoId;

    @LocalServerPort
    private int porta;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void datasourceEIamRealDeTeste(DynamicPropertyRegistry registry) throws NoSuchAlgorithmException {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_login");
        registry.add("spring.datasource.password", () -> "app_login");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("siafic.security.database.require-ssl", () -> "false");

        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        registry.add("siafic.iam.enabled", () -> "true");
        registry.add("siafic.iam.govbr.issuer", () -> ISSUER);
        registry.add("siafic.iam.govbr.audience", () -> AUDIENCE);
        registry.add("siafic.iam.govbr.public-key-pem", ExecucaoEscritaRbacRealHttpIntegrationTest::publicKeyPem);

        registry.add("siafic.iam.concessoes[0].cpf", () -> CPF_PLANEJADOR);
        registry.add("siafic.iam.concessoes[0].ente-id", () -> ENTE);
        registry.add("siafic.iam.concessoes[0].papeis[0]", () -> "ADMIN_PLATAFORMA");

        registry.add("siafic.iam.concessoes[1].cpf", () -> CPF_LANCADOR);
        registry.add("siafic.iam.concessoes[1].ente-id", () -> ENTE);
        registry.add("siafic.iam.concessoes[1].papeis[0]", () -> "LANCADOR");

        registry.add("siafic.iam.concessoes[2].cpf", () -> CPF_PAGADOR);
        registry.add("siafic.iam.concessoes[2].ente-id", () -> ENTE);
        registry.add("siafic.iam.concessoes[2].papeis[0]", () -> "PAGADOR");
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
            st.execute(("insert into ente (id, cnpj, nome, esfera) values ('%s', '99999999999999', 'Ente RAZ-218', "
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

    @Test
    @Order(1)
    void lancadorNaoConsegueFixarDotacaoViaHttpReal() {
        Map<String, Object> corpo = Map.of("fixacoes", List.of(fixacao()), "creditos", List.of());

        ResponseEntity<Map> resposta = post("/execucao/dotacoes:lote", CPF_LANCADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).containsEntry("codigo", "sem_permissao");
    }

    @Test
    @Order(2)
    void adminPlataformaFixaDotacaoViaHttpReal() {
        Map<String, Object> corpo = Map.of("fixacoes", List.of(fixacao()), "creditos", List.of());

        ResponseEntity<Map> resposta = post("/execucao/dotacoes:lote", CPF_PLANEJADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(207);
        List<String> inseridas = (List<String>) resposta.getBody().get("dotacoesInseridas");
        assertThat(inseridas).hasSize(1);
        assertThat((List<?>) resposta.getBody().get("erros")).isEmpty();
        dotacaoId = inseridas.get(0);
    }

    @Test
    @Order(3)
    void lancadorEmpenhaViaHttpReal() {
        Map<String, Object> corpo = Map.of(
                "dotacaoId", dotacaoId,
                "tipo", "ordinario",
                "credorId", UUID.randomUUID().toString(),
                "unidadeGestoraId", UUID.randomUUID().toString(),
                "valor", "4200.00",
                "dataFato", "2026-07-15",
                "exercicio", 2026,
                "classificacaoOrcamentaria", "raz-218",
                "fonteRecurso", "raz-218",
                "historico", "empenho via HTTP real com RBAC real (RAZ-218)");

        ResponseEntity<Map> resposta = post("/execucao/empenhos", CPF_LANCADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).containsEntry("valor", "4200.00");
        empenhoId = (String) resposta.getBody().get("id");
    }

    @Test
    @Order(4)
    void lancadorLiquidaViaHttpReal() {
        Map<String, Object> documento =
                Map.of("tipo", "nota_fiscal", "numero", "NF-218", "dataEmissao", "2026-07-14");
        Map<String, Object> corpo = Map.of(
                "empenhoId", empenhoId,
                "dataCompetencia", "2026-07-16",
                "valor", "4200.00",
                "documentosSuporte", List.of(documento),
                "historico", "liquidacao via HTTP real com RBAC real (RAZ-218)");

        ResponseEntity<Map> resposta = post("/execucao/liquidacoes", CPF_LANCADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).containsEntry("status", "pendente");
        liquidacaoId = (String) resposta.getBody().get("id");
    }

    @Test
    @Order(5)
    void pagadorAprovaViaHttpReal() {
        Map<String, Object> corpo = Map.of("decisao", "aprovar");

        ResponseEntity<Map> resposta =
                post("/execucao/liquidacoes/" + liquidacaoId + "/aprovacao", CPF_PAGADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsEntry("status", "aprovada");
    }

    @Test
    @Order(6)
    void pagadorPagaViaHttpReal() {
        Map<String, Object> beneficiario = Map.of("nome", "Fornecedor RAZ-218", "cpfCnpj", "12345678901");
        Map<String, Object> corpo = Map.of(
                "liquidacaoId", liquidacaoId,
                "dataCompetencia", "2026-07-20",
                "valor", "4200.00",
                "natureza", "orcamentario",
                "beneficiario", beneficiario,
                "historico", "pagamento via HTTP real com RBAC real (RAZ-218)");

        ResponseEntity<Map> resposta = post("/execucao/pagamentos", CPF_PAGADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).containsEntry("valor", "4200.00");
        assertThat(resposta.getBody().get("fatoContabilId")).isNotNull();
    }

    private static Map<String, Object> fixacao() {
        Map<String, Object> fixacao = new java.util.HashMap<>();
        fixacao.put("exercicio", 2026);
        fixacao.put("classificacaoOrcamentaria", "raz-218");
        fixacao.put("fonteRecurso", "raz-218");
        fixacao.put("unidadeGestoraId", UUID.randomUUID().toString());
        fixacao.put("valorAutorizado", "150000.00");
        return fixacao;
    }

    private <T> ResponseEntity<T> post(String caminho, String cpfAtor, Object corpo, Class<T> tipoResposta) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwt(cpfAtor));
        return restTemplate.exchange(url(caminho), HttpMethod.POST, new HttpEntity<>(corpo, headers), tipoResposta);
    }

    private String url(String caminho) {
        return "http://localhost:%d/api/v1/entes/%s%s".formatted(porta, ENTE, caminho);
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Assina uma asserção gov.br RS256 real (nível OURO, MFA forte) para o CPF informado. */
    private static String jwt(String cpf) {
        try {
            String header = base64Url(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
            String payload = base64Url(JSON.writeValueAsBytes(Map.of(
                    "iss", ISSUER,
                    "aud", AUDIENCE,
                    "exp", Instant.now().plusSeconds(600).getEpochSecond(),
                    "cpf", cpf,
                    "ente_id", ENTE,
                    "nivel_conta", "OURO")));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            String signingInput = header + "." + payload;
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + base64Url(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("falha ao assinar JWT gov.br de teste", e);
        }
    }

    private static String publicKeyPem() {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
