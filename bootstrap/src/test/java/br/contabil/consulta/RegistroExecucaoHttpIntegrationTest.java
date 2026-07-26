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
 * RAZ-121: prova pela borda HTTP real o gap encontrado no RAZ-120 — a tela de "execuções
 * registradas" não tinha consulta persistida, só cache de sessão do React Query. Registra
 * dois empenhos, liquida/aprova/paga um deles, e prova (A) {@code GET /empenhos} lista mais
 * recente primeiro com filtro por {@code exercicio}/período e pagina por cursor, (B) {@code
 * GET /liquidacoes/registradas} mostra a liquidação MESMO para o próprio autor — ao contrário
 * de {@code GET /liquidacoes} (fila de aprovação, ADR-0029 §2), que a esconderia dele (Regra
 * 9) —, (C) {@code GET /pagamentos} não vaza o beneficiário (04-lgpd). Mesmo arcabouço de
 * {@code FilaETrilhaAprovacaoHttpIntegrationTest}/{@code ExecucaoEscritaHttpIntegrationTest}.
 */
@org.springframework.boot.test.context.SpringBootTest(
        classes = RazaoApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import(RegistroExecucaoHttpIntegrationTest.ServicoIdentidadeDeTeste.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegistroExecucaoHttpIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE = "99999999-9999-9999-9999-999999999999";
    private static final String CPF_ORDENADOR = "11111111111";
    private static final String CPF_APROVADOR = "22222222222";

    private static String dotacaoId;
    private static String empenhoAntigoId;
    private static String empenhoRecenteId;
    private static String liquidacaoId;
    private static String pagamentoId;

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
            st.execute(("insert into ente (id, cnpj, nome, esfera) values ('%s', '99999999999999', 'Ente RAZ-121', "
                            + "'municipio')")
                    .formatted(ENTE));
            st.execute(("insert into periodo_contabil (ente_id, exercicio, mes, status) "
                            + "values ('%s', 2026, 7, 'aberto')")
                    .formatted(ENTE));
            st.execute(("insert into periodo_contabil (ente_id, exercicio, mes, status) "
                            + "values ('%s', 2025, 12, 'aberto')")
                    .formatted(ENTE));

            for (String[] conta : contasPcaspNecessarias()) {
                st.execute(("insert into conta_pcasp (ente_id, codigo, descricao, natureza_informacao, natureza_saldo) "
                                + "values ('%s', '%s', '%s', '%s', 'D')")
                        .formatted(ENTE, conta[0], conta[1], conta[2]));
            }

            try (ResultSet rs = st.executeQuery(("insert into dotacao (ente_id, exercicio, classificacao_orcamentaria, "
                            + "fonte_recurso, unidade_gestora_id, valor_autorizado) "
                            + "values ('%s', 2026, 'raz-121', 'raz-121', '%s', 100000.00) returning id")
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
    void registraDoisEmpenhosEmExerciciosDiferentes() {
        empenhoAntigoId = empenha(2025, "2025-12-10", "500.00", "empenho antigo (RAZ-121)");
        empenhoRecenteId = empenha(2026, "2026-07-15", "12300.00", "empenho recente (RAZ-121)");
    }

    @Test
    @Order(2)
    void empenhosListaOsDoisMaisRecentePrimeiro() {
        ResponseEntity<Map> resposta = get("/execucao/empenhos", CPF_APROVADOR, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> itens = (List<Map<String, Object>>) resposta.getBody().get("itens");
        List<Object> ids = itens.stream().map(i -> i.get("id")).toList();
        assertThat(ids).containsSubsequence(empenhoRecenteId, empenhoAntigoId);
        Map<String, Object> recente =
                itens.stream().filter(i -> empenhoRecenteId.equals(i.get("id"))).findFirst().orElseThrow();
        assertThat(recente).containsEntry("valor", "12300.00");
        assertThat(recente).containsEntry("tipo", "ordinario");
        assertThat(recente).containsEntry("status", "registrado");
        assertThat(recente.get("credorId")).isNotNull();
    }

    @Test
    @Order(3)
    void empenhosFiltraPorExercicio() {
        ResponseEntity<Map> resposta = get("/execucao/empenhos?exercicio=2025", CPF_APROVADOR, Map.class);

        List<Map<String, Object>> itens = (List<Map<String, Object>>) resposta.getBody().get("itens");
        assertThat(itens).extracting(i -> i.get("id")).containsExactly(empenhoAntigoId);
    }

    @Test
    @Order(4)
    void empenhosPaginaPorCursorSemRepetirNemPular() {
        ResponseEntity<Map> primeiraPagina = get("/execucao/empenhos?limit=1", CPF_APROVADOR, Map.class);
        List<Map<String, Object>> itens1 = (List<Map<String, Object>>) primeiraPagina.getBody().get("itens");
        assertThat(itens1).hasSize(1);
        assertThat(itens1.get(0).get("id")).isEqualTo(empenhoRecenteId);
        String cursor = (String) primeiraPagina.getBody().get("proximoCursor");
        assertThat(cursor).isNotNull();

        ResponseEntity<Map> segundaPagina =
                get("/execucao/empenhos?limit=1&cursor=" + cursor, CPF_APROVADOR, Map.class);
        List<Map<String, Object>> itens2 = (List<Map<String, Object>>) segundaPagina.getBody().get("itens");
        assertThat(itens2).extracting(i -> i.get("id")).containsExactly(empenhoAntigoId);
    }

    @Test
    @Order(5)
    void liquidaAprovaEPagaOEmpenhoRecente() {
        Map<String, Object> documento = Map.of("tipo", "nota_fiscal", "numero", "NF-121", "dataEmissao", "2026-07-14");
        Map<String, Object> liquidacao = Map.of(
                "empenhoId", empenhoRecenteId,
                "dataCompetencia", "2026-07-16",
                "valor", "4200.00",
                "documentosSuporte", List.of(documento),
                "historico", "liquidacao registro (RAZ-121)");
        ResponseEntity<Map> respLiquidacao = post("/execucao/liquidacoes", CPF_ORDENADOR, liquidacao, Map.class);
        assertThat(respLiquidacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        liquidacaoId = (String) respLiquidacao.getBody().get("id");

        ResponseEntity<Map> respAprovacao = post(
                "/execucao/liquidacoes/" + liquidacaoId + "/aprovacao", CPF_APROVADOR, Map.of("decisao", "aprovar"), Map.class);
        assertThat(respAprovacao.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> beneficiario = Map.of("nome", "Fornecedor RAZ-121", "cpfCnpj", "12345678901");
        Map<String, Object> pagamento = Map.of(
                "liquidacaoId", liquidacaoId,
                "dataCompetencia", "2026-07-20",
                "valor", "4200.00",
                "natureza", "orcamentario",
                "beneficiario", beneficiario,
                "historico", "pagamento registro (RAZ-121)");
        ResponseEntity<Map> respPagamento = post("/execucao/pagamentos", CPF_APROVADOR, pagamento, Map.class);
        assertThat(respPagamento.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        pagamentoId = (String) respPagamento.getBody().get("id");
    }

    @Test
    @Order(6)
    void liquidacoesRegistradasMostraAoProprioAutorAoContrarioDaFilaDeAprovacao() {
        ResponseEntity<Map> registro = get("/execucao/liquidacoes/registradas", CPF_ORDENADOR, Map.class);
        assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> itensRegistro = (List<Map<String, Object>>) registro.getBody().get("itens");
        Map<String, Object> item = itensRegistro.stream()
                .filter(i -> liquidacaoId.equals(i.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("registro deveria mostrar a própria liquidação ao autor"));
        assertThat(item).containsEntry("valor", "4200.00");
        assertThat(item).containsEntry("statusAprovacao", "aprovada");
        assertThat(item).containsEntry("empenhoId", empenhoRecenteId);

        // contraste: a fila de aprovação (gate, Regra 9) esconde a mesma liquidação do autor.
        ResponseEntity<Map> fila = get("/execucao/liquidacoes?statusAprovacao=aprovada", CPF_ORDENADOR, Map.class);
        List<Map<String, Object>> itensFila = (List<Map<String, Object>>) fila.getBody().get("itens");
        assertThat(itensFila).noneSatisfy(i -> assertThat(i.get("id")).isEqualTo(liquidacaoId));
    }

    @Test
    @Order(7)
    void pagamentosListaSemVazarBeneficiario() {
        ResponseEntity<Map> resposta = get("/execucao/pagamentos", CPF_APROVADOR, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> itens = (List<Map<String, Object>>) resposta.getBody().get("itens");
        Map<String, Object> item =
                itens.stream().filter(i -> pagamentoId.equals(i.get("id"))).findFirst().orElseThrow();
        assertThat(item).containsEntry("valor", "4200.00");
        assertThat(item).containsEntry("natureza", "orcamentario");
        assertThat(item).containsEntry("liquidacaoId", liquidacaoId);
        assertThat(item).doesNotContainKeys("beneficiario", "beneficiarioNome", "beneficiarioCpfCnpj");
    }

    private String empenha(int exercicio, String dataFato, String valor, String historico) {
        Map<String, Object> corpo = Map.of(
                "dotacaoId", dotacaoId,
                "tipo", "ordinario",
                "credorId", UUID.randomUUID().toString(),
                "unidadeGestoraId", UUID.randomUUID().toString(),
                "valor", valor,
                "dataFato", dataFato,
                "exercicio", exercicio,
                "classificacaoOrcamentaria", "raz-121",
                "fonteRecurso", "raz-121",
                "historico", historico);
        ResponseEntity<Map> resposta = post("/execucao/empenhos", CPF_ORDENADOR, corpo, Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resposta.getBody().get("id");
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
