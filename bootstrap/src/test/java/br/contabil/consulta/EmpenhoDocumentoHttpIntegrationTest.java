package br.contabil.consulta;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.contabil.RazaoApplication;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.documento.ArmazenamentoDocumentos;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;

/**
 * RAZ-157/ADR-0039 decisão 2: prova pela borda HTTP real que {@code GET
 * .../empenhos/{id}/documento} serve o PDF pendente quando {@code
 * PENDENTE_ASSINATURA}, o assinado (nunca o pendente) quando {@code ASSINADO},
 * 404 {@code documento_nao_encontrado} quando não há documento ou o empenho não
 * existe, e nunca resolve um empenho de outro ente (R3, RAZ-45) — mesmo
 * arcabouço de {@code RegistroExecucaoHttpIntegrationTest}/{@code
 * ExecucaoEscritaHttpIntegrationTest}, com {@link ArmazenamentoDocumentos}
 * trocado por um duplo em memória (Testcontainers cobre só o Postgres real —
 * RLS/tenant/status são o que este teste prova; a leitura do object store em si
 * já é coberta por {@code S3ArmazenamentoDocumentosTest}).
 */
@org.springframework.boot.test.context.SpringBootTest(
        classes = RazaoApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import({
    EmpenhoDocumentoHttpIntegrationTest.ServicoIdentidadeDeTeste.class,
    EmpenhoDocumentoHttpIntegrationTest.ArmazenamentoDocumentosDeTeste.class
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmpenhoDocumentoHttpIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE_A = "77777777-7777-7777-7777-777777777777";
    private static final String ENTE_B = "66666666-6666-6666-6666-666666666666";
    private static final String CPF_ENTE_A = "11111111111";
    private static final String CPF_ENTE_B = "22222222222";

    private static String dotacaoIdEnteA;
    private static String dotacaoIdEnteB;
    private static String empenhoPendenteId;
    private static String empenhoAssinadoId;
    private static String empenhoSemDocumentoId;
    private static String empenhoDeOutroEnteId;

    @LocalServerPort
    private int porta;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private ArmazenamentoDocumentos armazenamentoDocumentos;

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
                            Optional.empty(),
                            true,
                            Instant.parse("2030-01-01T00:00:00Z"));
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

    /** Duplo de teste: object store em memória, sem MinIO real (RAZ-157 aceita "fake" — mesma leitura de bytes). */
    @TestConfiguration
    static class ArmazenamentoDocumentosDeTeste {
        @Bean
        @Primary
        ArmazenamentoDocumentos armazenamentoDocumentosDeTeste() {
            return new ArmazenamentoDocumentos() {
                private final Map<URI, byte[]> conteudos = new ConcurrentHashMap<>();

                @Override
                public byte[] ler(URI referencia) {
                    byte[] conteudo = conteudos.get(referencia);
                    if (conteudo == null) {
                        throw new DocumentoNaoEncontradoException("documento ausente na referência: " + referencia);
                    }
                    return conteudo;
                }

                @Override
                public URI armazenar(byte[] conteudo, URI destino) {
                    conteudos.put(destino, conteudo);
                    return destino;
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
                st.execute(("insert into ente (id, cnpj, nome, esfera) values ('%s', '%s', 'Ente RAZ-157 %s', "
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
                        + "values ('%s', 2026, 'raz-157', 'raz-157', '%s', 100000.00) returning id")
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
        empenhoPendenteId = empenha(ENTE_A, CPF_ENTE_A, dotacaoIdEnteA, "pendente de assinatura (RAZ-157)");
        empenhoAssinadoId = empenha(ENTE_A, CPF_ENTE_A, dotacaoIdEnteA, "assinado (RAZ-157)");
        empenhoSemDocumentoId = empenha(ENTE_A, CPF_ENTE_A, dotacaoIdEnteA, "registrado sem documento (RAZ-157)");
        empenhoDeOutroEnteId = empenha(ENTE_B, CPF_ENTE_B, dotacaoIdEnteB, "empenho do ente B (RAZ-157)");
    }

    @Test
    @Order(2)
    void marcaOsDocumentosNoBancoEPoeOsBytesNoObjectStoreDeTeste() throws SQLException {
        URI uriPendente = uriDoEnte(ENTE_A, empenhoPendenteId + "-pendente.pdf");
        marcarPendenteAssinatura(ENTE_A, empenhoPendenteId, uriPendente);
        armazenamentoDocumentos.armazenar("bytes-pendente".getBytes(), uriPendente);

        URI uriAssinadaPendenteOrigem = uriDoEnte(ENTE_A, empenhoAssinadoId + "-pendente.pdf");
        URI uriAssinado = uriDoEnte(ENTE_A, empenhoAssinadoId + "-assinado.pdf");
        marcarPendenteAssinatura(ENTE_A, empenhoAssinadoId, uriAssinadaPendenteOrigem);
        marcarAssinado(ENTE_A, empenhoAssinadoId, uriAssinado);
        armazenamentoDocumentos.armazenar("bytes-assinado".getBytes(), uriAssinado);
        // nunca grava bytes na origem pendente do empenho assinado — se o endpoint ler o pendente por engano,
        // ArmazenamentoDocumentos.ler estoura DocumentoNaoEncontradoException em vez de silenciosamente servir o certo.

        URI uriOutroEnte = uriDoEnte(ENTE_B, empenhoDeOutroEnteId + "-pendente.pdf");
        marcarPendenteAssinatura(ENTE_B, empenhoDeOutroEnteId, uriOutroEnte);
        armazenamentoDocumentos.armazenar("bytes-outro-ente".getBytes(), uriOutroEnte);
    }

    @Test
    @Order(3)
    void servePendenteQuandoStatusEhPendenteAssinatura() {
        ResponseEntity<byte[]> resposta = getPdf(ENTE_A, CPF_ENTE_A, empenhoPendenteId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(resposta.getBody()).isEqualTo("bytes-pendente".getBytes());
    }

    @Test
    @Order(4)
    void serveOAssinadoENuncaOPendenteQuandoStatusEhAssinado() {
        ResponseEntity<byte[]> resposta = getPdf(ENTE_A, CPF_ENTE_A, empenhoAssinadoId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEqualTo("bytes-assinado".getBytes());
    }

    @Test
    @Order(5)
    void quatroZeroQuatroQuandoRegistradoSemDocumentoAinda() {
        ResponseEntity<Map> resposta = getErro(ENTE_A, CPF_ENTE_A, empenhoSemDocumentoId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).containsEntry("codigo", "documento_nao_encontrado");
    }

    @Test
    @Order(6)
    void quatroZeroQuatroQuandoEmpenhoNaoExiste() {
        ResponseEntity<Map> resposta = getErro(ENTE_A, CPF_ENTE_A, UUID.randomUUID().toString());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).containsEntry("codigo", "documento_nao_encontrado");
    }

    @Test
    @Order(7)
    void quatroZeroQuatroQuandoOEmpenhoEhDeOutroEnteNuncaVazaOConteudo() {
        ResponseEntity<Map> resposta = getErro(ENTE_A, CPF_ENTE_A, empenhoDeOutroEnteId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).containsEntry("codigo", "documento_nao_encontrado");

        // controle: o mesmo empenho existe e serve normalmente para a sessão do próprio ente B.
        ResponseEntity<byte[]> respostaDoDono = getPdf(ENTE_B, CPF_ENTE_B, empenhoDeOutroEnteId);
        assertThat(respostaDoDono.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaDoDono.getBody()).isEqualTo("bytes-outro-ente".getBytes());
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
                "classificacaoOrcamentaria", "raz-157",
                "fonteRecurso", "raz-157",
                "historico", historico);
        ResponseEntity<Map> resposta = restTemplate.exchange(
                url(ente, "/execucao/empenhos"),
                HttpMethod.POST,
                new HttpEntity<>(corpo, headers(cpfAtor)),
                Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resposta.getBody().get("id");
    }

    private void marcarPendenteAssinatura(String ente, String empenhoId, URI uri) throws SQLException {
        try (Connection admin = adminConnection();
                var stmt = admin.prepareStatement(
                        "update empenho set status = 'pendente_assinatura', documento_pendente_uri = ? "
                                + "where ente_id = ? and id = ?")) {
            stmt.setString(1, uri.toString());
            stmt.setObject(2, UUID.fromString(ente));
            stmt.setObject(3, UUID.fromString(empenhoId));
            stmt.executeUpdate();
        }
    }

    private void marcarAssinado(String ente, String empenhoId, URI uriAssinado) throws SQLException {
        try (Connection admin = adminConnection()) {
            try (var stmt = admin.prepareStatement("update empenho set status = 'assinado' where ente_id = ? and id = ?")) {
                stmt.setObject(1, UUID.fromString(ente));
                stmt.setObject(2, UUID.fromString(empenhoId));
                stmt.executeUpdate();
            }
            try (var stmt = admin.prepareStatement(
                    "insert into execucao_empenho_documento_assinado (ente_id, empenho_id, uri_pdf_assinado, "
                            + "hash_sha256, manifesto, transacao_id, nivel_assinatura, signatario_cpf, assinado_em) "
                            + "values (?, ?, ?, 'hash-raz-157', 'manifesto-raz-157', ?, 'AVANCADA_GOVBR', "
                            + "'11122233344', now())")) {
                stmt.setObject(1, UUID.fromString(ente));
                stmt.setObject(2, UUID.fromString(empenhoId));
                stmt.setString(3, uriAssinado.toString());
                stmt.setObject(4, UUID.randomUUID());
                stmt.executeUpdate();
            }
        }
    }

    private static URI uriDoEnte(String ente, String sufixo) {
        return URI.create("s3://ged/%s/%s".formatted(ente, sufixo));
    }

    private ResponseEntity<byte[]> getPdf(String ente, String cpfAtor, String empenhoId) {
        return restTemplate.exchange(
                url(ente, "/execucao/empenhos/" + empenhoId + "/documento"),
                HttpMethod.GET,
                new HttpEntity<>(null, headers(cpfAtor)),
                byte[].class);
    }

    private ResponseEntity<Map> getErro(String ente, String cpfAtor, String empenhoId) {
        return restTemplate.exchange(
                url(ente, "/execucao/empenhos/" + empenhoId + "/documento"),
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
