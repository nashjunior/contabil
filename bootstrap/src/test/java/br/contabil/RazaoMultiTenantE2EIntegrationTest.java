package br.contabil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.application.ConsultarSaldo;
import br.contabil.razao.application.EstornarFatoContabil;
import br.contabil.razao.application.RegistrarFatoContabil;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.TipoEvento;
import br.contabil.razao.domain.repository.ConsultaSaldoPort;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RAZ-52: fecha o gap multi-tenant do ciclo completo do razão — {@link TenantContextWiringIntegrationTest}
 * (RAZ-21) prova isolamento de numeração entre dois entes, mas só para {@code registrar}; {@link
 * RegistrarEstornarFatoContabilIntegrationTest} (RAZ-27) prova o ciclo registrar→estornar→saldo pelo
 * pipeline real, mas só com UM ente. Nenhum dos dois prova RLS de fato (nega leitura cross-tenant sem
 * filtro explícito na query) nem o anti-BOLA de {@code ControleAcesso} (RAZ-33) na trilha do razão.
 *
 * <p>Mesmo wiring de produção dos dois testes acima (advisors de {@code TransacaoUseCasesConfiguration}
 * + {@code TenantContextUseCasesConfiguration}, datasource de runtime como {@code app_login} sob RLS
 * forçada) — sem nenhum {@code SET LOCAL} manual no caminho de ESCRITA.
 *
 * <p><b>Achado ao escrever este teste (RAZ-52), resolvido em RAZ-59</b>: {@link ConsultaSaldoPort}
 * (bean real, sem use case próprio em {@code razao-application}) NÃO era coberto pelo pointcut de
 * {@code TenantContextUseCasesConfiguration} ({@code execution(* br.contabil..application..*.executar(..))}
 * — só ele fecha {@code app.ente_id}) e, chamado direto (fora de um {@code executar(..)} advisado),
 * quebrava com {@code DataAccessException} numa conexão pooled que já teve {@code app.ente_id} setado
 * antes: {@code set_config(name, valor, true)} dentro de uma transação que fecha NÃO devolve o GUC para
 * NULL na mesma sessão — devolve string vazia (reproduzido isolado contra Postgres 16 puro), e a policy
 * {@code current_setting('app.ente_id', true)::uuid} não trata isso, então o cast estoura. Efeito:
 * fail-closed (nunca vazava dado cross-tenant), mas como exceção, não como "0 linhas".
 *
 * <p>RAZ-59 fechou a lacuna com {@link ConsultarSaldo} (use case real em {@code razao-application},
 * mesmo padrão de {@link RegistrarFatoContabil}/{@link EstornarFatoContabil}) — o teste 6 abaixo prova
 * o caminho correto. O teste 5 continua travando o comportamento do bypass direto do port (quem injeta
 * {@link ConsultaSaldoPort} sem passar pelo use case ainda falha fechado, não vaza) — não é mais o único
 * caminho, mas segue como guarda de regressão contra esse uso indevido. As leituras de saldo nos demais
 * testes usam o mesmo helper de SQL cru com {@code set_config} manual que RAZ-27 já usa para verificação
 * (não é caminho de produção, só prova o dado sob RLS).
 */
@SpringBootTest(classes = RazaoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(RazaoMultiTenantE2EIntegrationTest.ServicoIdentidadePermissivoParaTeste.class)
class RazaoMultiTenantE2EIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /**
     * RAZ-33: prova o pipeline multi-tenant (RAZ-52), não RBAC/MFA — substitui o bean por um
     * duplo permissivo. {@link br.contabil.plataforma.domain.iam.ControleAcesso} continua real
     * (bean de produção construído sobre este duplo): o anti-BOLA (tenant da sessão × tenant da
     * requisição) roda de verdade, só o RBAC de fundo (autorizar) é sempre permissivo.
     */
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

    private static Sessao sessaoAutenticada(TenantId ente) {
        return new Sessao(
                UUID.randomUUID(), new Cpf("12345678901"), ente, Optional.empty(), true,
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    private static final String ENTE_A = "aa111111-1111-1111-1111-111111111111";
    private static final String ENTE_B = "bb222222-2222-2222-2222-222222222222";
    private static final String PERIODO_A = "aaaaaaaa-a111-a111-a111-aaaaaaaaaaaa";
    private static final String PERIODO_B = "aaaaaaaa-b222-b222-b222-aaaaaaaaaaaa";

    @Autowired
    private RegistrarFatoContabil registrarFatoContabil;

    @Autowired
    private EstornarFatoContabil estornarFatoContabil;

    @Autowired
    private ConsultaSaldoPort consultaSaldoPort;

    @Autowired
    private ConsultarSaldo consultarSaldo;

    @BeforeAll
    static void migraCriaLoginDeMenorPrivilegioESemeiaDoisEntes() throws SQLException {
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
                      ('%s', '11111111111111', 'Ente RAZ-52 A', 'municipio'),
                      ('%s', '22222222222222', 'Ente RAZ-52 B', 'municipio')
                    """.formatted(ENTE_A, ENTE_B));
            st.execute("""
                    insert into periodo_contabil (id, ente_id, exercicio, mes, status) values
                      ('%s', '%s', 2026, 1, 'aberto'),
                      ('%s', '%s', 2026, 1, 'aberto')
                    """.formatted(PERIODO_A, ENTE_A, PERIODO_B, ENTE_B));
        }
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

    // ------------------------------------------------------------------
    // 1. Ciclo completo (registrar -> estornar -> saldo) para DOIS entes
    //    através do pipeline real — estornar A não pode afetar o saldo de B.
    //    Contas frescas por teste: saldo é SUM sobre a conta inteira, e a
    //    classe inteira compartilha um único Postgres/container (mais rápido
    //    que subir um por teste) — reusar CONTA_A/CONTA_B entre métodos
    //    tornaria os valores absolutos de saldo dependentes da ordem de
    //    execução dos testes (JUnit não garante ordem de declaração).
    // ------------------------------------------------------------------

    @Test
    void registrarEstornarESaldoFicamIsoladosEntreDoisEntesAtravesDoPipelineReal() throws SQLException {
        TenantId enteA = TenantId.de(ENTE_A);
        TenantId enteB = TenantId.de(ENTE_B);
        // Débito e crédito em CONTAS DIFERENTES — na mesma conta o saldo líquido zeraria
        // por design contábil (é exatamente isso que o teste 1 quer distinguir do estorno).
        UUID contaDebitoA = criarConta(ENTE_A, "RAZ52-1A-DEB");
        UUID contaCreditoA = criarConta(ENTE_A, "RAZ52-1A-CRED");
        UUID contaDebitoB = criarConta(ENTE_B, "RAZ52-1B-DEB");
        UUID contaCreditoB = criarConta(ENTE_B, "RAZ52-1B-CRED");

        FatoContabil fatoA = registrarFatoContabil.executar(
                sessaoAutenticada(enteA), enteA, LocalDate.of(2026, 1, 10), TipoEvento.EMPENHO,
                "empenho ente A (RAZ-52)", "test",
                List.of(
                        Lancamento.de(contaDebitoA, Natureza.DEBITO, Dinheiro.de("500.00")),
                        Lancamento.de(contaCreditoA, Natureza.CREDITO, Dinheiro.de("500.00"))));
        registrarFatoContabil.executar(
                sessaoAutenticada(enteB), enteB, LocalDate.of(2026, 1, 11), TipoEvento.EMPENHO,
                "empenho ente B (RAZ-52)", "test",
                List.of(
                        Lancamento.de(contaDebitoB, Natureza.DEBITO, Dinheiro.de("300.00")),
                        Lancamento.de(contaCreditoB, Natureza.CREDITO, Dinheiro.de("300.00"))));

        assertThat(saldoDevedorLiquidoViaRls(enteA, contaDebitoA)).isEqualByComparingTo("500.00");
        assertThat(saldoDevedorLiquidoViaRls(enteB, contaDebitoB)).isEqualByComparingTo("300.00");

        FatoContabil estornoA = estornarFatoContabil.executar(
                sessaoAutenticada(enteA), enteA, fatoA.id(), LocalDate.of(2026, 1, 15),
                "estorno ente A (RAZ-52)", "test");

        assertThat(estornoA.isEstorno()).isTrue();
        assertThat(saldoDevedorLiquidoViaRls(enteA, contaDebitoA))
                .as("estorno de A neutraliza o saldo de A")
                .isEqualByComparingTo("0.00");
        assertThat(saldoDevedorLiquidoViaRls(enteB, contaDebitoB))
                .as("estorno de A não pode vazar efeito para o saldo de B — entes isolados")
                .isEqualByComparingTo("300.00");
    }

    // ------------------------------------------------------------------
    // 2. RLS de verdade: query SEM filtro explícito de ente_id só devolve
    //    as linhas do ente setado em app.ente_id — nunca as do outro ente.
    // ------------------------------------------------------------------

    @Test
    void rlsForcadaEscondeFatosDeOutroEnteMesmoSemFiltroExplicitoNaQuery() throws SQLException {
        TenantId enteA = TenantId.de(ENTE_A);
        TenantId enteB = TenantId.de(ENTE_B);
        UUID contaA = criarConta(ENTE_A, "RAZ52-2A");
        UUID contaB = criarConta(ENTE_B, "RAZ52-2B");

        registrarFatoContabil.executar(
                sessaoAutenticada(enteA), enteA, LocalDate.of(2026, 1, 12), TipoEvento.RECEITA,
                "fato A visivel só para A", "test",
                List.of(
                        Lancamento.de(contaA, Natureza.DEBITO, Dinheiro.de("10.00")),
                        Lancamento.de(contaA, Natureza.CREDITO, Dinheiro.de("10.00"))));
        registrarFatoContabil.executar(
                sessaoAutenticada(enteB), enteB, LocalDate.of(2026, 1, 12), TipoEvento.RECEITA,
                "fato B visivel só para B", "test",
                List.of(
                        Lancamento.de(contaB, Natureza.DEBITO, Dinheiro.de("20.00")),
                        Lancamento.de(contaB, Natureza.CREDITO, Dinheiro.de("20.00"))));

        List<String> entesVisiveisParaA = fatoContabilEnteIdsSobRls(enteA);

        assertThat(entesVisiveisParaA)
                .as("select * sem WHERE, sob RLS com app.ente_id = A — nenhuma linha de B pode aparecer")
                .isNotEmpty()
                .allMatch(enteId -> enteId.equals(ENTE_A));
    }

    // ------------------------------------------------------------------
    // 3. Numeração por sessão (RAZ-14) permanece isolada por ente mesmo
    //    com registro E estorno intercalados entre os dois entes — a
    //    interação RAZ-14×RAZ-21 que já quebrou o contexto uma vez.
    //    Assertivas RELATIVAS (delta), não absolutas: contador_fato é por
    //    ENTE (não por conta), e os quatro testes desta classe compartilham
    //    os mesmos dois entes — o valor absoluto de numeroSeq depende de
    //    quantos outros testes já rodaram antes neste ente.
    // ------------------------------------------------------------------

    @Test
    void numeracaoPermaneceIsoladaComRegistroEEstornoIntercaladosEntreEntes() throws SQLException {
        TenantId enteA = TenantId.de(ENTE_A);
        TenantId enteB = TenantId.de(ENTE_B);
        UUID contaA = criarConta(ENTE_A, "RAZ52-3A");
        UUID contaB = criarConta(ENTE_B, "RAZ52-3B");

        FatoContabil fatoA1 = registrarFatoContabil.executar(
                sessaoAutenticada(enteA), enteA, LocalDate.of(2026, 1, 13), TipoEvento.EMPENHO,
                "A#1", "test",
                List.of(
                        Lancamento.de(contaA, Natureza.DEBITO, Dinheiro.de("1.00")),
                        Lancamento.de(contaA, Natureza.CREDITO, Dinheiro.de("1.00"))));
        FatoContabil fatoB1 = registrarFatoContabil.executar(
                sessaoAutenticada(enteB), enteB, LocalDate.of(2026, 1, 13), TipoEvento.EMPENHO,
                "B#1", "test",
                List.of(
                        Lancamento.de(contaB, Natureza.DEBITO, Dinheiro.de("1.00")),
                        Lancamento.de(contaB, Natureza.CREDITO, Dinheiro.de("1.00"))));
        // estorno de A intercalado ANTES do próximo registro de B — se a numeração
        // vazasse entre entes, o próximo numeroSeq de B pularia por causa do estorno de A.
        FatoContabil estornoA1 = estornarFatoContabil.executar(
                sessaoAutenticada(enteA), enteA, fatoA1.id(), LocalDate.of(2026, 1, 14), "estorno A#1", "test");
        FatoContabil fatoB2 = registrarFatoContabil.executar(
                sessaoAutenticada(enteB), enteB, LocalDate.of(2026, 1, 14), TipoEvento.EMPENHO,
                "B#2", "test",
                List.of(
                        Lancamento.de(contaB, Natureza.DEBITO, Dinheiro.de("1.00")),
                        Lancamento.de(contaB, Natureza.CREDITO, Dinheiro.de("1.00"))));

        assertThat(estornoA1.numeroSeq())
                .as("estorno consome o próximo numero_seq de A, contíguo ao registro anterior de A")
                .isEqualTo(fatoA1.numeroSeq() + 1);
        assertThat(fatoB2.numeroSeq())
                .as("estorno de A (que também consumiu numero_seq de A) não pode furar a sequência de B")
                .isEqualTo(fatoB1.numeroSeq() + 1);
    }

    // ------------------------------------------------------------------
    // 4. Anti-BOLA: ControleAcesso (RAZ-33) rejeita ANTES de qualquer I/O
    //    quando o tenant da requisição diverge do tenant da sessão —
    //    mesmo com o RBAC de fundo sempre permissivo neste teste.
    // ------------------------------------------------------------------

    @Test
    void controleAcessoRejeitaTenantDaRequisicaoDivergenteDaSessaoAntesDeQualquerIO() throws SQLException {
        TenantId enteA = TenantId.de(ENTE_A);
        TenantId enteB = TenantId.de(ENTE_B);
        UUID contaB = criarConta(ENTE_B, "RAZ52-4B");
        String descricao = "sessão de A tentando registrar em nome de B (RAZ-52 teste 4)";

        assertThatThrownBy(() -> registrarFatoContabil.executar(
                        sessaoAutenticada(enteA), enteB, LocalDate.of(2026, 1, 16), TipoEvento.EMPENHO,
                        descricao, "test",
                        List.of(
                                Lancamento.de(contaB, Natureza.DEBITO, Dinheiro.de("999.00")),
                                Lancamento.de(contaB, Natureza.CREDITO, Dinheiro.de("999.00")))))
                .as("anti-BOLA: tenant da sessão (A) não bate com o tenant da requisição (B)")
                .isInstanceOf(ServicoIdentidade.SemPermissaoException.class);

        assertThat(contarFatosComDescricao(descricao))
                .as("nada foi persistido para B — rejeitado antes de qualquer I/O")
                .isZero();
    }

    // ------------------------------------------------------------------
    // 5. Guarda de regressão (RAZ-52): quem BYPASSA o use case e injeta
    //    ConsultaSaldoPort direto ainda cai fora do pointcut de
    //    TenantContextUseCasesConfiguration — falha fechado (exceção),
    //    não "0 sem contexto". Não é mais o caminho correto (ver teste 6
    //    e ConsultarSaldo, RAZ-59), mas continua sendo o comportamento
    //    exigido de quem usa o port errado.
    // ------------------------------------------------------------------

    @Test
    void consultaSaldoPortForaDoAdvisorFalhaFechadoEmVezDeVazarOuDevolverSilenciosamenteZero() throws SQLException {
        TenantId enteA = TenantId.de(ENTE_A);
        UUID contaA = criarConta(ENTE_A, "RAZ52-5A");
        // Garante que a conexão pooled já passou por um executar(..) advisado antes desta
        // chamada — é essa sequência (advisado, depois direto) que reproduz o GUC customizado
        // "usado e revertido para vazio" em vez de nulo na mesma sessão (ver javadoc da classe).
        registrarFatoContabil.executar(
                sessaoAutenticada(enteA), enteA, LocalDate.of(2026, 1, 17), TipoEvento.RECEITA,
                "aquece a conexão pooled com app.ente_id setado", "test",
                List.of(
                        Lancamento.de(contaA, Natureza.DEBITO, Dinheiro.de("5.00")),
                        Lancamento.de(contaA, Natureza.CREDITO, Dinheiro.de("5.00"))));

        assertThatThrownBy(() -> consultaSaldoPort.saldoDevedorLiquido(enteA, contaA))
                .as("achado RAZ-52: bypass do port direto ainda falha fechado (nunca devolve dado "
                        + "de outro tenant) — mas como exceção, não como consulta vazia; ver javadoc da classe")
                .isInstanceOf(DataAccessException.class);
    }

    // ------------------------------------------------------------------
    // 6. RAZ-59: o caminho CORRETO — ConsultarSaldo (use case real, cai
    //    no advisor) funciona mesmo na MESMA conexão pooled que acabou de
    //    ser usada fora do advisor no teste anterior (prova que o use
    //    case reseta app.ente_id de verdade, não só evita o problema por
    //    sorte de ordem de execução).
    // ------------------------------------------------------------------

    @Test
    void consultarSaldoPeloUseCaseFuncionaMesmoDepoisDeUmaChamadaDiretaAoPortForaDoAdvisor() throws SQLException {
        TenantId enteA = TenantId.de(ENTE_A);
        TenantId enteB = TenantId.de(ENTE_B);
        // Débito e crédito em CONTAS DIFERENTES — na mesma conta o saldo líquido zeraria
        // (mesma ressalva do teste 1: é exatamente esse o ponto que o teste quer medir).
        UUID contaDebitoA = criarConta(ENTE_A, "RAZ59-6A-DEB");
        UUID contaCreditoA = criarConta(ENTE_A, "RAZ59-6A-CRED");
        registrarFatoContabil.executar(
                sessaoAutenticada(enteA), enteA, LocalDate.of(2026, 1, 18), TipoEvento.RECEITA,
                "RAZ-59 saldo via use case", "test",
                List.of(
                        Lancamento.de(contaDebitoA, Natureza.DEBITO, Dinheiro.de("42.00")),
                        Lancamento.de(contaCreditoA, Natureza.CREDITO, Dinheiro.de("42.00"))));
        // chamada direta ao port (mesmo padrão do teste 5) para deixar app.ente_id "usado e
        // revertido para vazio" na conexão antes de provar que o use case ainda funciona.
        assertThatThrownBy(() -> consultaSaldoPort.saldoDevedorLiquido(enteA, contaDebitoA))
                .isInstanceOf(DataAccessException.class);

        Dinheiro saldoA = consultarSaldo.executar(sessaoAutenticada(enteA), enteA, contaDebitoA);

        assertThat(saldoA).isEqualByComparingTo(Dinheiro.de("42.00"));
        assertThatThrownBy(() -> consultarSaldo.executar(sessaoAutenticada(enteB), enteA, contaDebitoA))
                .as("anti-BOLA: sessão do ente B não pode consultar saldo em nome do ente A via ConsultarSaldo")
                .isInstanceOf(ServicoIdentidade.SemPermissaoException.class);
    }

    private UUID criarConta(String enteId, String codigo) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection dono = adminConnection();
                Statement st = dono.createStatement()) {
            st.execute(
                    """
                    insert into conta_pcasp (id, ente_id, codigo, descricao, natureza_informacao, natureza_saldo)
                    values ('%s', '%s', '%s', 'Conta %s', 'patrimonial', 'D')
                    """
                            .formatted(id, enteId, codigo, codigo));
        }
        return id;
    }

    private static long contarFatosComDescricao(String historico) throws SQLException {
        try (Connection conn = adminConnection();
                PreparedStatement ps =
                        conn.prepareStatement("select count(*) from fato_contabil where historico = ?")) {
            ps.setString(1, historico);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static List<String> fatoContabilEnteIdsSobRls(TenantId ente) throws SQLException {
        List<String> enteIds = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login")) {
            conn.setAutoCommit(false);
            try (PreparedStatement setTenant = conn.prepareStatement("select set_config('app.ente_id', ?, true)")) {
                setTenant.setString(1, ente.valor().toString());
                setTenant.execute();
            }
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery("select ente_id from fato_contabil")) {
                while (rs.next()) {
                    enteIds.add(rs.getString("ente_id"));
                }
            } finally {
                conn.rollback();
            }
        }
        return enteIds;
    }

    /** Leitura de verificação sob RLS com {@code set_config} manual — mesmo padrão de RAZ-27, não é caminho de produção. */
    private static BigDecimal saldoDevedorLiquidoViaRls(TenantId enteId, UUID contaId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
                Statement st = conn.createStatement()) {
            conn.setAutoCommit(false);
            st.execute("select set_config('app.ente_id', '" + enteId.valor() + "', true)");
            try (ResultSet rs = st.executeQuery(
                    "select saldo_devedor_liquido from saldo_conta where conta_id = '" + contaId + "'")) {
                if (!rs.next()) {
                    return BigDecimal.ZERO.setScale(2);
                }
                return rs.getBigDecimal(1);
            } finally {
                conn.rollback();
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
