package br.contabil.sessao;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import br.contabil.RazaoApplication;

/**
 * Ponta a ponta real (RAZ-228/ADR-0052 item 1): {@code POST /sessao/dev-idp/token} assina
 * um bearer que o verificador REAL ({@code VerificadorJwtGovBr}, via {@code
 * ServicoIdentidadeGovBrIcp}) aceita em {@code GET /sessao/atual} — prova que o par de
 * chaves de dev configurado por {@code siafic.dev-idp.*} realmente conversa com {@code
 * siafic.iam.govbr.*} (mesma env var em produção, ver README), sem duplicar a
 * verificação num mock/duplo — é a mesma mecânica que {@code
 * ExecucaoEscritaRbacRealHttpIntegrationTest} já prova para o restante da borda HTTP.
 */
@SpringBootTest(classes = RazaoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class SessaoDevIdpHttpIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ISSUER = "https://e2e.siafic.local/idp";
    private static final String AUDIENCE = "siafic-e2e";
    private static final String CPF = "12345678901";
    private static final String ENTE = "99999999-9999-9999-9999-999999999999";

    @LocalServerPort
    private int porta;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void datasourceEDevIdpDeTeste(DynamicPropertyRegistry registry) throws NoSuchAlgorithmException {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_login");
        registry.add("spring.datasource.password", () -> "app_login");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("siafic.security.database.require-ssl", () -> "false");

        KeyPairGenerator geradorDeChaves = KeyPairGenerator.getInstance("RSA");
        geradorDeChaves.initialize(2048);
        KeyPair par = geradorDeChaves.generateKeyPair();
        String publicKeyPem = pem(par.getPublic().getEncoded(), "PUBLIC");

        registry.add("siafic.iam.enabled", () -> "true");
        registry.add("siafic.iam.govbr.issuer", () -> ISSUER);
        registry.add("siafic.iam.govbr.audience", () -> AUDIENCE);
        registry.add("siafic.iam.govbr.public-key-pem", () -> publicKeyPem);
        registry.add("siafic.iam.concessoes[0].cpf", () -> CPF);
        registry.add("siafic.iam.concessoes[0].ente-id", () -> ENTE);
        registry.add("siafic.iam.concessoes[0].papeis[0]", () -> "LANCADOR");

        registry.add("siafic.dev-idp.enabled", () -> "true");
        registry.add("siafic.dev-idp.issuer", () -> ISSUER);
        registry.add("siafic.dev-idp.audience", () -> AUDIENCE);
        registry.add("siafic.dev-idp.public-key-pem", () -> publicKeyPem);
        registry.add("siafic.dev-idp.private-key-pem", () -> pem(par.getPrivate().getEncoded(), "PRIVATE"));
    }

    @BeforeAll
    static void migraESemeiaRoleDeRuntime() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement st = admin.createStatement()) {
            st.execute("create role app_login login password 'app_login'");
            st.execute("grant app_role to app_login");
        }
    }

    @Test
    void bearerEmitidoPeloDevIdpEAceitoPeloVerificadorRealEmSessaoAtual() {
        ResponseEntity<Map> respostaToken = restTemplate.postForEntity(
                url("/sessao/dev-idp/token"), Map.of("cpf", CPF, "enteId", ENTE, "papel", "LANCADOR"), Map.class);

        assertThat(respostaToken.getStatusCode()).isEqualTo(HttpStatus.OK);
        String bearerToken = (String) respostaToken.getBody().get("bearerToken");
        assertThat(bearerToken).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + bearerToken);
        ResponseEntity<Map> respostaSessao = restTemplate.exchange(
                url("/sessao/atual"), HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(respostaSessao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaSessao.getBody()).containsEntry("enteId", ENTE);
        assertThat(respostaSessao.getBody()).containsEntry("cpfMascarado", "***.456.***-**");
    }

    @Test
    void emitirTokenComCpfInvalidoDevolve400() {
        ResponseEntity<Map> resposta = restTemplate.postForEntity(
                url("/sessao/dev-idp/token"), Map.of("cpf", "123", "enteId", ENTE, "papel", "LANCADOR"), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).containsEntry("erro", "cpf_invalido");
    }

    private String url(String caminho) {
        return "http://localhost:%d%s".formatted(porta, caminho);
    }

    private static String pem(byte[] der, String tipo) {
        return "-----BEGIN " + tipo + " KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + tipo + " KEY-----";
    }
}
