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
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FonteRecurso;
import br.contabil.razao.domain.repository.DisponibilidadePorFontePort;

/**
 * RAZ-225: prova que o roteiro DDR (classes 7/8 PCASP — ADR-0054) escritura as
 * partidas de controle certas nos três estágios da despesa vinculada a uma
 * fonte de recursos (FR), no <b>mesmo</b> {@code FatoContabil} que as partidas
 * orçamentárias/patrimoniais (Σ=Σ global pelo trigger diferido).
 *
 * <p>Complementa {@link ExecucaoRazaoFatoContabilIntegrationTest} (RAZ-213),
 * que cobre o roteiro sem FR (DDR não provisionado ⇒ {@code resolverContaOpcional}
 * retorna vazio ⇒ nenhuma partida de controle adicionada). Aqui as contas DDR
 * estão provisionadas e o empenho carrega {@code fonteRecurso} → o adapter
 * DEVE acrescentar as partidas de controle ao mesmo fato.
 *
 * <p>[REVALIDAR] códigos 8.2.x contra MCASP edição vigente antes de produção
 * (mesma marcação de docs/17 e docs/15).
 */
@org.springframework.boot.test.context.SpringBootTest(
        classes = RazaoApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import(DdrFatoContabilIntegrationTest.ServicoIdentidadeDeTeste.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DdrFatoContabilIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE = "dddddddd-dddd-dddd-dddd-dddddddddddd";
    private static final String CPF_ATOR = "11111111111";
    private static final String CPF_APROVADOR = "22222222222";
    private static final String FONTE_RECURSO = "1500";

    // contas orçamentárias/patrimoniais
    private static final String COD_CREDITO_DISPONIVEL = "6.2.2.1.1";
    private static final String COD_EMPENHADO_A_LIQUIDAR = "6.2.2.1.3";
    private static final String COD_LIQUIDADO_A_PAGAR = "6.2.2.1.4";
    private static final String COD_EMPENHADO_PAGO = "6.2.2.1.5";
    private static final String COD_VPD = "3.3.3.1.01";
    private static final String COD_FORNECEDORES = "2.1.3";
    private static final String COD_CAIXA = "1.1.1";

    // contas DDR [REVALIDAR]
    private static final String COD_DDR_DISPONIVEL = "8.2.1.1.1";
    private static final String COD_DDR_COMPROMETIDA = "8.2.1.1.2";
    private static final String COD_DDR_EM_LIQUIDACAO = "8.2.2.1.2";
    private static final String COD_DDR_UTILIZADA = "8.2.3.1.1";

    private static String dotacaoId;
    private static String empenhoId;
    private static String fatoEmpenhoId;
    private static String liquidacaoId;
    private static String fatoLiquidacaoId;
    private static String fatoPagamentoId;

    @LocalServerPort
    private int porta;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private DisponibilidadePorFontePort disponibilidadePorFonte;

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
                    throw new UnsupportedOperationException("fake de teste");
                }
            };
        }
    }

    @BeforeAll
    static void migraESemeia() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection admin = adminConn(); Statement st = admin.createStatement()) {
            st.execute("create role app_login login password 'app_login'");
            st.execute("grant app_role to app_login");
            st.execute("insert into ente (id, cnpj, nome, esfera) values ('%s', '55555555555555', 'Ente DDR', 'municipio')"
                    .formatted(ENTE));
            st.execute("insert into periodo_contabil (ente_id, exercicio, mes, status) values ('%s', 2026, 8, 'aberto')"
                    .formatted(ENTE));

            for (String[] c : todasAsContas()) {
                st.execute(("insert into conta_pcasp (ente_id, codigo, descricao, natureza_informacao, natureza_saldo) "
                        + "values ('%s', '%s', '%s', '%s', 'D')").formatted(ENTE, c[0], c[1], c[2]));
            }

            try (ResultSet rs = st.executeQuery(
                    ("insert into dotacao (ente_id, exercicio, classificacao_orcamentaria, fonte_recurso, "
                            + "unidade_gestora_id, valor_autorizado) "
                            + "values ('%s', 2026, 'ddr-225', '1500', '%s', 500000.00) returning id")
                            .formatted(ENTE, UUID.randomUUID()))) {
                rs.next();
                dotacaoId = rs.getString(1);
            }
        }
    }

    private static String[][] todasAsContas() {
        return new String[][] {
            {COD_CREDITO_DISPONIVEL, "Credito Disponivel", "orcamentaria"},
            {COD_EMPENHADO_A_LIQUIDAR, "Credito Empenhado a Liquidar", "orcamentaria"},
            {COD_LIQUIDADO_A_PAGAR, "Empenhado Liquidado a Pagar", "orcamentaria"},
            {COD_EMPENHADO_PAGO, "Empenhado Pago", "orcamentaria"},
            {COD_VPD, "VPD de servicos", "patrimonial"},
            {COD_FORNECEDORES, "Fornecedores a Pagar", "patrimonial"},
            {COD_CAIXA, "Caixa e Bancos", "patrimonial"},
            // contas DDR — presença ativa o roteiro de controle [REVALIDAR]
            {COD_DDR_DISPONIVEL, "DDR a Comprometer", "controle"},
            {COD_DDR_COMPROMETIDA, "DDR Comprometida por Empenho", "controle"},
            {COD_DDR_EM_LIQUIDACAO, "DDR em Liquidacao", "controle"},
            {COD_DDR_UTILIZADA, "DDR Utilizada", "controle"},
        };
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
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
    @DisplayName("empenho com FR=1500: 2 orcamentarias + 2 DDR (D comprometida / C disponivel), Sd=Sc")
    void empenhoComFrEscriturar4LancamentosComDdr() throws SQLException {
        Map<String, Object> corpo = Map.of(
                "dotacaoId", dotacaoId,
                "tipo", "ordinario",
                "credorId", UUID.randomUUID().toString(),
                "unidadeGestoraId", UUID.randomUUID().toString(),
                "valor", "10000.00",
                "dataFato", "2026-08-01",
                "exercicio", 2026,
                "classificacaoOrcamentaria", "ddr-225",
                "fonteRecurso", FONTE_RECURSO,
                "historico", "empenho DDR RAZ-225");

        ResponseEntity<Map> resposta = post("/execucao/empenhos", CPF_ATOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        empenhoId = (String) resposta.getBody().get("id");
        fatoEmpenhoId = (String) resposta.getBody().get("fatoContabilId");

        assertLancamentosExatos(
                fatoEmpenhoId,
                // orcamentario
                lancamento(COD_EMPENHADO_A_LIQUIDAR, 'D', "10000.00", null),
                lancamento(COD_CREDITO_DISPONIVEL, 'C', "10000.00", null),
                // DDR controle — com fonte_recurso = "1500"
                lancamento(COD_DDR_COMPROMETIDA, 'D', "10000.00", FONTE_RECURSO),
                lancamento(COD_DDR_DISPONIVEL, 'C', "10000.00", FONTE_RECURSO));
    }

    @Test
    @Order(2)
    @DisplayName("liquidacao: 4 orcamentarias/patrimoniais + 2 DDR (D em-liquidacao / C comprometida), Sd=Sc")
    void liquidacaoComFrEscriturar6LancamentosComDdr() throws SQLException {
        Map<String, Object> documento = Map.of("tipo", "nota_fiscal", "numero", "NF-225", "dataEmissao", "2026-08-01");
        Map<String, Object> corpo = Map.of(
                "empenhoId", empenhoId,
                "dataCompetencia", "2026-08-02",
                "valor", "3000.00",
                "documentosSuporte", List.of(documento),
                "historico", "liquidacao DDR RAZ-225");

        ResponseEntity<Map> resposta = post("/execucao/liquidacoes", CPF_ATOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        liquidacaoId = (String) resposta.getBody().get("id");
        fatoLiquidacaoId = (String) resposta.getBody().get("fatoContabilId");

        assertLancamentosExatos(
                fatoLiquidacaoId,
                // patrimonial
                lancamento(COD_VPD, 'D', "3000.00", null),
                lancamento(COD_FORNECEDORES, 'C', "3000.00", null),
                // orcamentario
                lancamento(COD_LIQUIDADO_A_PAGAR, 'D', "3000.00", null),
                lancamento(COD_EMPENHADO_A_LIQUIDAR, 'C', "3000.00", null),
                // DDR controle — com fonte_recurso = "1500"
                lancamento(COD_DDR_EM_LIQUIDACAO, 'D', "3000.00", FONTE_RECURSO),
                lancamento(COD_DDR_COMPROMETIDA, 'C', "3000.00", FONTE_RECURSO));
    }

    @Test
    @Order(3)
    @DisplayName("pagamento: 4 orcamentarias/patrimoniais + 2 DDR (D utilizada / C em-liquidacao), Sd=Sc")
    void pagamentoComFrEscriturar6LancamentosComDdr() throws SQLException {
        // aprovar a liquidação antes de pagar
        post("/execucao/liquidacoes/" + liquidacaoId + "/aprovacao", CPF_APROVADOR,
                Map.of("decisao", "aprovar"), Map.class);

        Map<String, Object> beneficiario = Map.of("nome", "Fornecedor DDR", "cpfCnpj", "12345678901");
        Map<String, Object> corpo = new java.util.HashMap<>();
        corpo.put("liquidacaoId", liquidacaoId);
        corpo.put("dataCompetencia", "2026-08-03");
        corpo.put("valor", "3000.00");
        corpo.put("natureza", "orcamentario");
        corpo.put("beneficiario", beneficiario);
        corpo.put("historico", "pagamento DDR RAZ-225");

        ResponseEntity<Map> resposta = post("/execucao/pagamentos", CPF_APROVADOR, corpo, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        fatoPagamentoId = (String) resposta.getBody().get("fatoContabilId");

        assertLancamentosExatos(
                fatoPagamentoId,
                // patrimonial
                lancamento(COD_FORNECEDORES, 'D', "3000.00", null),
                lancamento(COD_CAIXA, 'C', "3000.00", null),
                // orcamentario
                lancamento(COD_EMPENHADO_PAGO, 'D', "3000.00", null),
                lancamento(COD_LIQUIDADO_A_PAGAR, 'C', "3000.00", null),
                // DDR controle — com fonte_recurso = "1500"
                lancamento(COD_DDR_UTILIZADA, 'D', "3000.00", FONTE_RECURSO),
                lancamento(COD_DDR_EM_LIQUIDACAO, 'C', "3000.00", FONTE_RECURSO));
    }

    @Test
    @Order(4)
    @DisplayName("DisponibilidadePorFontePort: saldo DDR comprometida para fonte 1500 = 7000 (empenho 10000 - liquidado 3000)")
    void disponibilidadePorFonteRetornaSaldoDevedorCorreto() throws SQLException {
        // busca o id da conta 8.2.1.1.2 (DDR comprometida) para o ente
        ContaContabilId contaComprometida;
        try (Connection admin = adminConn();
                PreparedStatement ps = admin.prepareStatement(
                        "select id from conta_pcasp where ente_id = ?::uuid and codigo = ?")) {
            ps.setString(1, ENTE);
            ps.setString(2, COD_DDR_COMPROMETIDA);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("conta DDR comprometida deve existir").isTrue();
                contaComprometida = new ContaContabilId(UUID.fromString(rs.getString("id")));
            }
        }

        List<DisponibilidadePorFontePort.SaldoPorFonte> saldos =
                disponibilidadePorFonte.consultarSaldoPorFonte(
                        new TenantId(UUID.fromString(ENTE)), List.of(contaComprometida));

        assertThat(saldos).hasSize(1);
        DisponibilidadePorFontePort.SaldoPorFonte saldo = saldos.get(0);
        assertThat(saldo.fonte()).isEqualTo(FonteRecurso.de(FONTE_RECURSO));
        // D 10000 no empenho − C 3000 na liquidação = saldo devedor 7000
        assertThat(saldo.saldoDevedorLiquido().valor())
                .isEqualByComparingTo(new java.math.BigDecimal("7000.00"));
    }

    // ------------------------------------------------------------------
    // infra
    // ------------------------------------------------------------------

    private record LancamentoEsperado(String codigoPcasp, char natureza, BigDecimal valor, String fonteRecurso) {}

    private static LancamentoEsperado lancamento(String cod, char nat, String val, String fr) {
        return new LancamentoEsperado(cod, nat, new BigDecimal(val), fr);
    }

    private static void assertLancamentosExatos(String fatoId, LancamentoEsperado... esperados) throws SQLException {
        List<LancamentoEsperado> encontrados = new ArrayList<>();
        BigDecimal somaD = BigDecimal.ZERO, somaC = BigDecimal.ZERO;
        try (Connection admin = adminConn();
                PreparedStatement ps = admin.prepareStatement("""
                        select cp.codigo, l.natureza, l.valor, l.fonte_recurso
                          from lancamento l join conta_pcasp cp on cp.id = l.conta_id
                         where l.fato_id = ?::uuid
                        """)) {
            ps.setString(1, fatoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    char nat = rs.getString("natureza").charAt(0);
                    BigDecimal val = rs.getBigDecimal("valor");
                    encontrados.add(new LancamentoEsperado(rs.getString("codigo"), nat, val, rs.getString("fonte_recurso")));
                    if (nat == 'D') somaD = somaD.add(val); else somaC = somaC.add(val);
                }
            }
        }
        assertThat(encontrados)
                .as("lançamentos do fato %s — conta/natureza/valor/fonte exatos (ADR-0054)", fatoId)
                .containsExactlyInAnyOrder(esperados);
        assertThat(somaD).as("Σdébito = Σcrédito no fato %s", fatoId).isEqualByComparingTo(somaC);
    }

    private <T> ResponseEntity<T> post(String caminho, String cpf, Object corpo, Class<T> tipo) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + cpf);
        return restTemplate.exchange(url(caminho), HttpMethod.POST, new HttpEntity<>(corpo, h), tipo);
    }

    private String url(String caminho) {
        return "http://localhost:%d/api/v1/entes/%s%s".formatted(porta, ENTE, caminho);
    }

    private static Connection adminConn() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
