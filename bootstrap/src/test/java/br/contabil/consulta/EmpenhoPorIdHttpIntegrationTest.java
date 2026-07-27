package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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
 * RAZ-156/ADR-0039 decisão 1: prova pela borda HTTP real que {@code GET
 * .../empenhos/{id}} devolve status + bloco {@code documento} para os 3
 * status do documento (PENDENTE_ASSINATURA/ASSINADO/ASSINATURA_REJEITADA),
 * 404 {@code empenho_nao_encontrado} para id inexistente ou de outro ente
 * (RLS), e NUNCA vaza a {@code s3://} crua do object store nem o CPF do
 * signatário em claro (R4) — mesmo arcabouço de {@code
 * EmpenhoDocumentoHttpIntegrationTest} (RAZ-157), sem precisar de {@code
 * ArmazenamentoDocumentos} porque este endpoint nunca lê o binário.
 */
@org.springframework.boot.test.context.SpringBootTest(
        classes = RazaoApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import(EmpenhoPorIdHttpIntegrationTest.ServicoIdentidadeDeTeste.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmpenhoPorIdHttpIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE_A = "55555555-5555-5555-5555-555555555555";
    private static final String ENTE_B = "44444444-4444-4444-4444-444444444444";
    private static final String CPF_ENTE_A = "11111111111";
    private static final String CPF_ENTE_B = "22222222222";
    private static final String CPF_SIGNATARIO = "98765432109";

    private static String dotacaoIdEnteA;
    private static String dotacaoIdEnteB;
    private static String empenhoRegistradoId;
    private static String empenhoPendenteId;
    private static String empenhoAssinadoId;
    private static String empenhoRejeitadoId;
    private static String empenhoDeOutroEnteId;

    @LocalServerPort
    private int porta;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    /** Duplo de teste: autentica cada CPF literal como a sessão de um ente fixo distinto. */
    @TestConfiguration
    static class ServicoIdentidadeDeTeste {
        @Bean
        @Primary
        ServicoIdentidade servicoIdentidadeDeTeste() {
            return new ServicoIdentidade() {
                @Override
                public Sessao autenticar(Credencial credencial) {
                    String cpf = ((CredencialGovBr) credencial).assercao();
                    String ente = CPF_ENTE_B.equals(cpf) ? ENTE_B : ENTE_A;
                    return new Sessao(
                            UUID.randomUUID(),
                            new Cpf(cpf),
                            new TenantId(UUID.fromString(ente)),
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
            for (String ente : new String[] {ENTE_A, ENTE_B}) {
                st.execute(("insert into ente (id, cnpj, nome, esfera) values ('%s', '%s', 'Ente RAZ-156 %s', "
                                + "'municipio')")
                        .formatted(ente, cnpjDe(ente), ente));
                st.execute(("insert into periodo_contabil (ente_id, exercicio, mes, status) "
                                + "values ('%s', 2026, 7, 'aberto')")
                        .formatted(ente));
                for (String[] conta : contasPcaspNecessarias()) {
                    st.execute(("insert into conta_pcasp (ente_id, codigo, descricao, natureza_informacao, "
                                    + "natureza_saldo) values ('%s', '%s', '%s', '%s', 'D')")
                            .formatted(ente, conta[0], conta[1], conta[2]));
                }
            }
            dotacaoIdEnteA = inserirDotacao(st, ENTE_A);
            dotacaoIdEnteB = inserirDotacao(st, ENTE_B);
        }
    }

    private static String cnpjDe(String ente) {
        return ente.replace("-", "").substring(0, 14);
    }

    private static String inserirDotacao(Statement st, String ente) throws SQLException {
        try (var rs = st.executeQuery(("insert into dotacao (ente_id, exercicio, classificacao_orcamentaria, "
                        + "fonte_recurso, unidade_gestora_id, valor_autorizado) "
                        + "values ('%s', 2026, 'raz-156', 'raz-156', '%s', 100000.00) returning id")
                .formatted(ente, UUID.randomUUID()))) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static String[][] contasPcaspNecessarias() {
        return new String[][] {
            {"6.2.2.1.1", "Credito Disponivel", "orcamentaria"},
            {"6.2.2.1.3", "Credito Empenhado a Liquidar", "orcamentaria"},
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
    void registraOsEmpenhosDeCenario() {
        empenhoRegistradoId = empenha(ENTE_A, CPF_ENTE_A, dotacaoIdEnteA, "registrado sem documento (RAZ-156)");
        empenhoPendenteId = empenha(ENTE_A, CPF_ENTE_A, dotacaoIdEnteA, "pendente de assinatura (RAZ-156)");
        empenhoAssinadoId = empenha(ENTE_A, CPF_ENTE_A, dotacaoIdEnteA, "assinado (RAZ-156)");
        empenhoRejeitadoId = empenha(ENTE_A, CPF_ENTE_A, dotacaoIdEnteA, "assinatura rejeitada (RAZ-156)");
        empenhoDeOutroEnteId = empenha(ENTE_B, CPF_ENTE_B, dotacaoIdEnteB, "empenho do ente B (RAZ-156)");
    }

    @Test
    @Order(2)
    void marcaOsDocumentosNoBanco() throws SQLException {
        marcarPendenteAssinatura(ENTE_A, empenhoPendenteId, uriDoEnte(ENTE_A, empenhoPendenteId + "-pendente.pdf"));

        marcarPendenteAssinatura(ENTE_A, empenhoAssinadoId, uriDoEnte(ENTE_A, empenhoAssinadoId + "-pendente.pdf"));
        marcarAssinado(ENTE_A, empenhoAssinadoId, uriDoEnte(ENTE_A, empenhoAssinadoId + "-assinado.pdf"));

        marcarPendenteAssinatura(ENTE_A, empenhoRejeitadoId, uriDoEnte(ENTE_A, empenhoRejeitadoId + "-pendente.pdf"));
        marcarAssinaturaRejeitada(ENTE_A, empenhoRejeitadoId);
    }

    @Test
    @Order(3)
    void registradoSemDocumentoDevolveDocumentoComOsDoisCamposNulos() {
        ResponseEntity<Map> resposta = get(ENTE_A, CPF_ENTE_A, empenhoRegistradoId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsEntry("status", "registrado");
        Map<String, Object> documento = (Map<String, Object>) resposta.getBody().get("documento");
        assertThat(documento.get("pendenteUri")).isNull();
        assertThat(documento.get("assinado")).isNull();
    }

    @Test
    @Order(4)
    void pendenteAssinaturaDevolvePendenteUriOpacoSemAssinado() {
        ResponseEntity<Map> resposta = get(ENTE_A, CPF_ENTE_A, empenhoPendenteId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsEntry("status", "pendente_assinatura");
        Map<String, Object> documento = (Map<String, Object>) resposta.getBody().get("documento");
        assertThat(documento.get("pendenteUri")).isNotNull();
        assertThat(documento.get("assinado")).isNull();
        assertThat(String.valueOf(documento.get("pendenteUri"))).doesNotContain("s3://");
    }

    @Test
    @Order(5)
    void assinaturaRejeitadaDevolvePendenteUriOpacoSemAssinado() {
        ResponseEntity<Map> resposta = get(ENTE_A, CPF_ENTE_A, empenhoRejeitadoId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsEntry("status", "assinatura_rejeitada");
        Map<String, Object> documento = (Map<String, Object>) resposta.getBody().get("documento");
        assertThat(documento.get("pendenteUri")).isNotNull();
        assertThat(documento.get("assinado")).isNull();
    }

    @Test
    @Order(6)
    void assinadoDevolveBlocoAssinadoComCpfMascaradoENuncaAS3CruaNemOCpfEmClaro() {
        ResponseEntity<Map> resposta = get(ENTE_A, CPF_ENTE_A, empenhoAssinadoId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsEntry("status", "assinado");
        Map<String, Object> documento = (Map<String, Object>) resposta.getBody().get("documento");
        // pendenteUri não vaza mais, mesmo o empenho tendo passado por PENDENTE_ASSINATURA antes.
        assertThat(documento.get("pendenteUri")).isNull();
        Map<String, Object> assinado = (Map<String, Object>) documento.get("assinado");
        assertThat(assinado).isNotNull();
        assertThat(assinado).containsEntry("hashSha256", "hash-raz-156");
        assertThat(assinado).containsEntry("nivel", "AVANCADA_GOVBR");
        assertThat(assinado.get("signatario")).isEqualTo("***.654.***-**").isNotEqualTo(CPF_SIGNATARIO);
        assertThat(assinado.get("idTransacao")).isNotNull();
        assertThat(assinado.get("assinadoEm")).isNotNull();

        String corpoCru = resposta.getBody().toString();
        assertThat(corpoCru).doesNotContain("s3://").doesNotContain(CPF_SIGNATARIO);
    }

    @Test
    @Order(7)
    void quatroZeroQuatroQuandoEmpenhoNaoExiste() {
        ResponseEntity<Map> resposta = get(ENTE_A, CPF_ENTE_A, UUID.randomUUID().toString());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).containsEntry("codigo", "empenho_nao_encontrado");
    }

    @Test
    @Order(8)
    void quatroZeroQuatroQuandoOEmpenhoEhDeOutroEnteNuncaVazaDado() {
        ResponseEntity<Map> resposta = get(ENTE_A, CPF_ENTE_A, empenhoDeOutroEnteId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).containsEntry("codigo", "empenho_nao_encontrado");

        // controle: o mesmo empenho existe e é lido normalmente pela sessão do próprio ente B.
        ResponseEntity<Map> respostaDoDono = get(ENTE_B, CPF_ENTE_B, empenhoDeOutroEnteId);
        assertThat(respostaDoDono.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String empenha(String ente, String cpfAtor, String dotacaoId, String historico) {
        Map<String, Object> corpo = Map.of(
                "dotacaoId", dotacaoId,
                "tipo", "ordinario",
                "credorId", UUID.randomUUID().toString(),
                "unidadeGestoraId", UUID.randomUUID().toString(),
                "valor", "1000.00",
                "dataFato", "2026-07-20",
                "exercicio", 2026,
                "classificacaoOrcamentaria", "raz-156",
                "fonteRecurso", "raz-156",
                "historico", historico);
        ResponseEntity<Map> resposta = restTemplate.exchange(
                url(ente, "/execucao/empenhos"),
                HttpMethod.POST,
                new HttpEntity<>(corpo, headers(cpfAtor)),
                Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resposta.getBody().get("id");
    }

    private void marcarPendenteAssinatura(String ente, String empenhoId, String uri) throws SQLException {
        try (Connection admin = adminConnection();
                var stmt = admin.prepareStatement(
                        "update empenho set status = 'pendente_assinatura', documento_pendente_uri = ? "
                                + "where ente_id = ? and id = ?")) {
            stmt.setString(1, uri);
            stmt.setObject(2, UUID.fromString(ente));
            stmt.setObject(3, UUID.fromString(empenhoId));
            stmt.executeUpdate();
        }
    }

    private void marcarAssinaturaRejeitada(String ente, String empenhoId) throws SQLException {
        try (Connection admin = adminConnection();
                var stmt = admin.prepareStatement(
                        "update empenho set status = 'assinatura_rejeitada' where ente_id = ? and id = ?")) {
            stmt.setObject(1, UUID.fromString(ente));
            stmt.setObject(2, UUID.fromString(empenhoId));
            stmt.executeUpdate();
        }
    }

    private void marcarAssinado(String ente, String empenhoId, String uriAssinado) throws SQLException {
        try (Connection admin = adminConnection()) {
            try (var stmt = admin.prepareStatement("update empenho set status = 'assinado' where ente_id = ? and id = ?")) {
                stmt.setObject(1, UUID.fromString(ente));
                stmt.setObject(2, UUID.fromString(empenhoId));
                stmt.executeUpdate();
            }
            try (var stmt = admin.prepareStatement(
                    "insert into execucao_empenho_documento_assinado (ente_id, empenho_id, uri_pdf_assinado, "
                            + "hash_sha256, manifesto, transacao_id, nivel_assinatura, signatario_cpf, assinado_em) "
                            + "values (?, ?, ?, 'hash-raz-156', 'manifesto-raz-156', ?, 'AVANCADA_GOVBR', ?, now())")) {
                stmt.setObject(1, UUID.fromString(ente));
                stmt.setObject(2, UUID.fromString(empenhoId));
                stmt.setString(3, uriAssinado);
                stmt.setObject(4, UUID.randomUUID());
                stmt.setString(5, CPF_SIGNATARIO);
                stmt.executeUpdate();
            }
        }
    }

    private static String uriDoEnte(String ente, String sufixo) {
        return "s3://ged/%s/%s".formatted(ente, sufixo);
    }

    private ResponseEntity<Map> get(String ente, String cpfAtor, String empenhoId) {
        return restTemplate.exchange(
                url(ente, "/execucao/empenhos/" + empenhoId),
                HttpMethod.GET,
                new HttpEntity<>(null, headers(cpfAtor)),
                Map.class);
    }

    private static HttpHeaders headers(String cpfAtor) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + cpfAtor);
        return headers;
    }

    private String url(String ente, String caminho) {
        return "http://localhost:%d/api/v1/entes/%s%s".formatted(porta, ente, caminho);
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
