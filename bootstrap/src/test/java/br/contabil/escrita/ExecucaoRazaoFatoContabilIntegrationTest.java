package br.contabil.escrita;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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
 * RAZ-213: prova, estágio a estágio, que a execução da despesa (empenho → liquidação →
 * pagamento) gera no razão o(s) FATO(S) CONTÁBIL(IS) CORRETO(S) — não só que a chamada HTTP
 * devolve {@code 201}/{@code fatoContabilId} (isso {@link ExecucaoEscritaHttpIntegrationTest},
 * RAZ-105, já cobre), mas que o {@code ExecucaoContabilPortAdapter} REAL, através do roteiro de
 * contabilização do ADR-0021, escriturou exatamente as contas PCASP certas, no lado
 * débito/crédito certo, pelo valor certo — lendo os lançamentos gravados em Postgres real
 * (Testcontainers) via o caminho de produção completo (controller → use case → port → motor do
 * razão), sem nenhum atalho de SQL direto.
 *
 * <p>{@link br.contabil.golden.GoldSetConformidadeContabilTest} (RAZ-19) prova o schema/as
 * travas do razão isolado de qualquer bug de aplicação, inserindo fato/lançamento direto via
 * SQL — deliberadamente sem passar pelo roteiro de {@code ExecucaoContabilPortAdapter}. Este
 * teste fecha o gap que o Javadoc daquele deixa em aberto: prova que o roteiro real (não uma
 * reencenação manual dele) fecha Σdébito=Σcrédito com as contas certas por subsistema PCASP —
 * exatamente o que o ADR-0021 pede ("precisa de teste de contabilização por estágio").
 *
 * <p>Mesmo arcabouço/wiring de produção de {@code ExecucaoEscritaHttpIntegrationTest}
 * (servidor HTTP real, datasource de runtime como {@code app_login} sob RLS forçada,
 * {@link ServicoIdentidade} substituído por um duplo que autentica qualquer bearer — o que está
 * sob teste aqui é a contabilização, não o RBAC).
 */
@org.springframework.boot.test.context.SpringBootTest(
        classes = RazaoApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import(ExecucaoRazaoFatoContabilIntegrationTest.ServicoIdentidadeDeTeste.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExecucaoRazaoFatoContabilIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE = "77777777-7777-7777-7777-777777777777";
    private static final String OUTRO_ENTE = "66666666-6666-6666-6666-666666666666";
    private static final String CPF_ORDENADOR = "11111111111";
    private static final String CPF_APROVADOR = "22222222222";

    private static final String CODIGO_CREDITO_DISPONIVEL = "6.2.2.1.1";
    private static final String CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR = "6.2.2.1.3";
    private static final String CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR = "6.2.2.1.4";
    private static final String CODIGO_EMPENHADO_PAGO = "6.2.2.1.5";
    private static final String CODIGO_VPD = "3.3.3.1.01";
    private static final String CODIGO_FORNECEDORES_A_PAGAR = "2.1.3";
    private static final String CODIGO_CAIXA_E_BANCOS = "1.1.1";

    private static String dotacaoId;

    private static String empenhoId;
    private static String fatoEmpenhoId;
    private static String liquidacaoId;
    private static String fatoLiquidacaoId;
    private static String pagamentoId;
    private static String fatoPagamentoId;

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
            st.execute(("insert into ente (id, cnpj, nome, esfera) values ('%s', '77777777777777', 'Ente RAZ-213', "
                            + "'municipio')")
                    .formatted(ENTE));
            st.execute(("insert into ente (id, cnpj, nome, esfera) values ('%s', '66666666666666', 'Ente RAZ-213 B', "
                            + "'municipio')")
                    .formatted(OUTRO_ENTE));
            st.execute(("insert into periodo_contabil (ente_id, exercicio, mes, status) "
                            + "values ('%s', 2026, 8, 'aberto')")
                    .formatted(ENTE));

            for (String[] conta : contasPcaspNecessarias()) {
                st.execute(("insert into conta_pcasp (ente_id, codigo, descricao, natureza_informacao, natureza_saldo) "
                                + "values ('%s', '%s', '%s', '%s', 'D')")
                        .formatted(ENTE, conta[0], conta[1], conta[2]));
            }

            try (ResultSet rs = st.executeQuery(("insert into dotacao (ente_id, exercicio, classificacao_orcamentaria, "
                            + "fonte_recurso, unidade_gestora_id, valor_autorizado) "
                            + "values ('%s', 2026, 'raz-213', 'raz-213', '%s', 100000.00) returning id")
                    .formatted(ENTE, UUID.randomUUID()))) {
                rs.next();
                dotacaoId = rs.getString(1);
            }
        }
    }

    private static String[][] contasPcaspNecessarias() {
        return new String[][] {
            {CODIGO_CREDITO_DISPONIVEL, "Credito Disponivel", "orcamentaria"},
            {CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR, "Credito Empenhado a Liquidar", "orcamentaria"},
            {CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR, "Empenhado Liquidado a Pagar", "orcamentaria"},
            {CODIGO_EMPENHADO_PAGO, "Empenhado Pago", "orcamentaria"},
            {CODIGO_VPD, "VPD de servicos", "patrimonial"},
            {CODIGO_FORNECEDORES_A_PAGAR, "Fornecedores a Pagar", "patrimonial"},
            {CODIGO_CAIXA_E_BANCOS, "Caixa e Bancos", "patrimonial"},
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
    @DisplayName("empenho: D Credito Empenhado a Liquidar (6.2.2.1.3) / C Credito Disponivel (6.2.2.1.1), Sd=Sc")
    void empenhoGeraFatoComAsContasOrcamentariasCertas() throws SQLException {
        Map<String, Object> corpo = Map.of(
                "dotacaoId", dotacaoId,
                "tipo", "ordinario",
                "credorId", UUID.randomUUID().toString(),
                "unidadeGestoraId", UUID.randomUUID().toString(),
                "valor", "12345.67",
                "dataFato", "2026-08-10",
                "exercicio", 2026,
                "classificacaoOrcamentaria", "raz-213",
                "fonteRecurso", "raz-213",
                "historico", "empenho RAZ-213");

        ResponseEntity<Map> resposta = post("/execucao/empenhos", CPF_ORDENADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        empenhoId = (String) resposta.getBody().get("id");
        fatoEmpenhoId = (String) resposta.getBody().get("fatoContabilId");
        assertThat(fatoEmpenhoId).isNotNull();

        assertFato(fatoEmpenhoId, "empenho", "execucao:empenho:" + empenhoId);
        assertLancamentosExatos(
                fatoEmpenhoId,
                lancamento(CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR, 'D', "12345.67"),
                lancamento(CODIGO_CREDITO_DISPONIVEL, 'C', "12345.67"));
    }

    @Test
    @Order(2)
    @DisplayName("liquidacao: patrimonial D VPD/C Fornecedores + orcamentario D Liquidado a Pagar/C Empenhado a Liquidar, Sd=Sc")
    void liquidacaoGeraUmUnicoFatoComOsDoisSubsistemasBalanceadosJuntos() throws SQLException {
        Map<String, Object> documento = Map.of("tipo", "nota_fiscal", "numero", "NF-213", "dataEmissao", "2026-08-09");
        Map<String, Object> corpo = Map.of(
                "empenhoId", empenhoId,
                "dataCompetencia", "2026-08-11",
                "valor", "4200.33",
                "documentosSuporte", List.of(documento),
                "historico", "liquidacao RAZ-213");

        ResponseEntity<Map> resposta = post("/execucao/liquidacoes", CPF_ORDENADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        liquidacaoId = (String) resposta.getBody().get("id");
        fatoLiquidacaoId = (String) resposta.getBody().get("fatoContabilId");
        assertThat(fatoLiquidacaoId).isNotNull();

        assertFato(fatoLiquidacaoId, "liquidacao", "execucao:liquidacao:" + liquidacaoId);
        assertLancamentosExatos(
                fatoLiquidacaoId,
                lancamento(CODIGO_VPD, 'D', "4200.33"),
                lancamento(CODIGO_FORNECEDORES_A_PAGAR, 'C', "4200.33"),
                lancamento(CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR, 'D', "4200.33"),
                lancamento(CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR, 'C', "4200.33"));
    }

    @Test
    @Order(3)
    @DisplayName("pagamento negado (liquidacao ainda pendente) nao escritura fato nenhum")
    void pagamentoRejeitadoAntesDaAprovacaoNaoAlteraORazao() throws SQLException {
        int fatosAntes = contarFatosDoEnte();

        ResponseEntity<Map> resposta = post("/execucao/pagamentos", CPF_APROVADOR, pagamentoValido(), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(contarFatosDoEnte())
                .as("tentativa rejeitada nao pode deixar rastro no razao (all-or-nothing)")
                .isEqualTo(fatosAntes);
    }

    @Test
    @Order(4)
    @DisplayName("pagamento apos aprovacao: patrimonial D Fornecedores/C Caixa + orcamentario D Empenhado Pago/C Liquidado a Pagar, Sd=Sc")
    void pagamentoGeraUmUnicoFatoComOsDoisSubsistemasBalanceadosJuntos() throws SQLException {
        ResponseEntity<Map> respostaAprovacao = post(
                "/execucao/liquidacoes/" + liquidacaoId + "/aprovacao",
                CPF_APROVADOR,
                Map.of("decisao", "aprovar"),
                Map.class);
        assertThat(respostaAprovacao.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> resposta = post("/execucao/pagamentos", CPF_APROVADOR, pagamentoValido(), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        pagamentoId = (String) resposta.getBody().get("id");
        fatoPagamentoId = (String) resposta.getBody().get("fatoContabilId");
        assertThat(fatoPagamentoId).isNotNull();

        assertFato(fatoPagamentoId, "pagamento", "execucao:pagamento:" + pagamentoId);
        assertLancamentosExatos(
                fatoPagamentoId,
                lancamento(CODIGO_FORNECEDORES_A_PAGAR, 'D', "4200.33"),
                lancamento(CODIGO_CAIXA_E_BANCOS, 'C', "4200.33"),
                lancamento(CODIGO_EMPENHADO_PAGO, 'D', "4200.33"),
                lancamento(CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR, 'C', "4200.33"));
    }

    @Test
    @Order(5)
    @DisplayName("os 3 fatos sao numerados em sequencia gapless (1,2,3) e o fato do empenho permanece intacto (append-only)")
    void osTresEstagiosProduzemNumeracaoGaplessEONaoAlteramFatosAnteriores() throws SQLException {
        assertThat(numeroSeqDoFato(fatoEmpenhoId)).isEqualTo(1L);
        assertThat(numeroSeqDoFato(fatoLiquidacaoId)).isEqualTo(2L);
        assertThat(numeroSeqDoFato(fatoPagamentoId)).isEqualTo(3L);

        // o fato do empenho, escriturado no Order(1), tem que estar intacto depois que
        // liquidacao e pagamento escrituraram fatos NOVOS por cima — nunca em cima dele.
        assertLancamentosExatos(
                fatoEmpenhoId,
                lancamento(CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR, 'D', "12345.67"),
                lancamento(CODIGO_CREDITO_DISPONIVEL, 'C', "12345.67"));

        assertThat(saldoLiquidoDaConta(CODIGO_CREDITO_EMPENHADO_A_LIQUIDAR))
                .as("liquidacao credita 6.2.2.1.3 e reduz o saldo a liquidar")
                .isEqualByComparingTo(new BigDecimal("8145.34"));
        assertThat(saldoLiquidoDaConta(CODIGO_EMPENHADO_LIQUIDADO_A_PAGAR))
                .as("pagamento credita 6.2.2.1.4 e zera o liquidado a pagar")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saldoLiquidoDaConta(CODIGO_EMPENHADO_PAGO))
                .as("pagamento abre o empenhado pago")
                .isEqualByComparingTo(new BigDecimal("4200.33"));
    }

    @Test
    @Order(6)
    @DisplayName("RLS: sessao de banco de OUTRO ente nao enxerga nenhum dos 3 fatos deste ente")
    void fatosDoEnteSobRlsSaoInvisiveisParaOutroEnte() throws SQLException {
        assertThat(contarFatosVisiveisComoEnte(ENTE))
                .as("o proprio ente enxerga os 3 fatos que ele escriturou")
                .isEqualTo(3);
        assertThat(contarFatosVisiveisComoEnte(OUTRO_ENTE))
                .as("RLS deny-by-default: sessao de outro ente nao ve fato_contabil nenhum deste ente")
                .isZero();
        assertThat(contarLancamentosVisiveisComoEnte(OUTRO_ENTE))
                .as("RLS deny-by-default: sessao de outro ente nao ve lancamento nenhum deste ente")
                .isZero();
    }

    // ------------------------------------------------------------------
    // Infraestrutura do teste
    // ------------------------------------------------------------------

    private record LancamentoEsperado(String codigoPcasp, char natureza, BigDecimal valor) {}

    private static LancamentoEsperado lancamento(String codigoPcasp, char natureza, String valor) {
        return new LancamentoEsperado(codigoPcasp, natureza, new BigDecimal(valor));
    }

    private static void assertFato(String fatoId, String tipoEventoEsperado, String origemEsperada) throws SQLException {
        try (Connection admin = adminConnection();
                PreparedStatement ps = admin.prepareStatement(
                        "select ente_id, tipo_evento, origem from fato_contabil where id = ?::uuid")) {
            ps.setString(1, fatoId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("fato %s existe", fatoId).isTrue();
                assertThat(rs.getString("ente_id")).isEqualTo(ENTE);
                assertThat(rs.getString("tipo_evento")).isEqualTo(tipoEventoEsperado);
                assertThat(rs.getString("origem")).isEqualTo(origemEsperada);
            }
        }
    }

    /** Confere o CONJUNTO exato de lançamentos do fato (conta PCASP + natureza + valor) — nenhum a mais, nenhum a menos. */
    private static void assertLancamentosExatos(String fatoId, LancamentoEsperado... esperados) throws SQLException {
        List<LancamentoEsperado> encontrados = new ArrayList<>();
        BigDecimal somaDebito = BigDecimal.ZERO;
        BigDecimal somaCredito = BigDecimal.ZERO;
        try (Connection admin = adminConnection();
                PreparedStatement ps = admin.prepareStatement("""
                        select cp.codigo, l.natureza, l.valor
                          from lancamento l join conta_pcasp cp on cp.id = l.conta_id
                         where l.fato_id = ?::uuid
                        """)) {
            ps.setString(1, fatoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    char natureza = rs.getString("natureza").charAt(0);
                    BigDecimal valor = rs.getBigDecimal("valor");
                    encontrados.add(new LancamentoEsperado(rs.getString("codigo"), natureza, valor));
                    if (natureza == 'D') {
                        somaDebito = somaDebito.add(valor);
                    } else {
                        somaCredito = somaCredito.add(valor);
                    }
                }
            }
        }

        assertThat(encontrados)
                .as("lançamentos do fato %s — contas/natureza/valor exatos do roteiro ADR-0021", fatoId)
                .containsExactlyInAnyOrder(esperados);
        assertThat(somaDebito)
                .as("Sigma debito = Sigma credito no fato %s (trava 1 do razao)", fatoId)
                .isEqualByComparingTo(somaCredito);
    }

    private static long numeroSeqDoFato(String fatoId) throws SQLException {
        try (Connection admin = adminConnection();
                PreparedStatement ps = admin.prepareStatement("select numero_seq from fato_contabil where id = ?::uuid")) {
            ps.setString(1, fatoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static BigDecimal saldoLiquidoDaConta(String codigoPcasp) throws SQLException {
        try (Connection admin = adminConnection();
                PreparedStatement ps = admin.prepareStatement("""
                        select coalesce(sum(case when l.natureza = 'D' then l.valor else -l.valor end), 0)
                          from lancamento l join conta_pcasp cp on cp.id = l.conta_id
                         where l.ente_id = ?::uuid and cp.codigo = ?
                        """)) {
            ps.setString(1, ENTE);
            ps.setString(2, codigoPcasp);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private static int contarFatosDoEnte() throws SQLException {
        try (Connection admin = adminConnection();
                PreparedStatement ps =
                        admin.prepareStatement("select count(*) from fato_contabil where ente_id = ?::uuid")) {
            ps.setString(1, ENTE);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int contarFatosVisiveisComoEnte(String enteIdDaSessao) throws SQLException {
        return comoAppLogin(enteIdDaSessao, st -> {
            try (ResultSet rs = st.executeQuery("select count(*) from fato_contabil where ente_id = '" + ENTE + "'")) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    private static int contarLancamentosVisiveisComoEnte(String enteIdDaSessao) throws SQLException {
        return comoAppLogin(enteIdDaSessao, st -> {
            try (ResultSet rs = st.executeQuery("select count(*) from lancamento where ente_id = '" + ENTE + "'")) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    @FunctionalInterface
    private interface OperacaoAppLogin<T> {
        T executar(Statement st) throws SQLException;
    }

    private static <T> T comoAppLogin(String enteId, OperacaoAppLogin<T> operacao) throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login")) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("set local app.ente_id = '" + enteId + "'");
                T resultado = operacao.executar(st);
                conn.commit();
                return resultado;
            }
        }
    }

    private static Map<String, Object> pagamentoValido() {
        Map<String, Object> beneficiario = Map.of("nome", "Fornecedor RAZ-213", "cpfCnpj", "12345678901");
        Map<String, Object> corpo = new java.util.HashMap<>();
        corpo.put("liquidacaoId", liquidacaoId);
        corpo.put("dataCompetencia", "2026-08-12");
        corpo.put("valor", "4200.33");
        corpo.put("natureza", "orcamentario");
        corpo.put("beneficiario", beneficiario);
        corpo.put("historico", "pagamento RAZ-213");
        return corpo;
    }

    private <T> ResponseEntity<T> post(String caminho, String cpfAtor, Object corpo, Class<T> tipoResposta) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + cpfAtor);
        return restTemplate.exchange(url(caminho), HttpMethod.POST, new HttpEntity<>(corpo, headers), tipoResposta);
    }

    private String url(String caminho) {
        return "http://localhost:%d/api/v1/entes/%s%s".formatted(porta, ENTE, caminho);
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
