package br.contabil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Cpf;
import br.contabil.plataforma.domain.iam.ServicoIdentidade.Sessao;
import br.contabil.razao.application.EstornarFatoContabil;
import br.contabil.razao.application.RegistrarFatoContabil;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.FatoContabil;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.PartidasNaoBalanceadasException;
import br.contabil.razao.domain.PeriodoContabilId;
import br.contabil.razao.domain.PeriodoEncerradoException;
import br.contabil.razao.domain.TipoEvento;

/**
 * RAZ-27: fecha o gap registrado no Javadoc de {@code GoldSetConformidadeContabilTest}
 * (RAZ-19) — até aqui só existiam dois lados desconectados: {@code RegistrarFatoContabilTest}/
 * {@code EstornarFatoContabilTest} (unitários, com mocks) e o próprio gold set
 * (integração real, mas via SQL cru autenticado como {@code app_login}, sem passar
 * pelo código Java de produção). Este teste sobe o contexto Spring de verdade
 * ({@link RazaoApplication}) e chama {@link RegistrarFatoContabil#executar}/
 * {@link EstornarFatoContabil#executar} como beans gerenciados — mesmo wiring de
 * produção que {@link TenantContextWiringIntegrationTest} (RAZ-21) já prova para o
 * registro isolado (advisors de {@code TransacaoUseCasesConfiguration} +
 * {@code TenantContextUseCasesConfiguration}, datasource de runtime apontando para
 * {@code app_login} sob RLS forçada) — ligados aos adapters reais de
 * {@code razao-infra} ({@code PostgresFatoContabilRepository}, {@code PostgresContadorFatoPort},
 * {@code PostgresPeriodoContabilPort}, {@code PostgresConsultaSaldoPort}) contra um
 * Postgres real (Testcontainers).
 */
@SpringBootTest(classes = RazaoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(RegistrarEstornarFatoContabilIntegrationTest.ServicoIdentidadePermissivoParaTeste.class)
class RegistrarEstornarFatoContabilIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /**
     * RAZ-33: em produção {@code ServicoIdentidadeIndisponivel} nega tudo
     * (RAZ-5 ainda não plugado). Este teste prova o pipeline real de
     * registrar/estornar (RAZ-27), não RBAC/MFA — substitui o bean por um
     * duplo permissivo, igual a qualquer outro adapter de teste.
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

    private static final String ENTE = "99999999-9999-9999-9999-999999999999";
    private static final String PERIODO_JANEIRO = "aaaaaaaa-0001-0001-0001-aaaaaaaaaaaa";
    private static final String PERIODO_FEVEREIRO = "aaaaaaaa-0002-0002-0002-aaaaaaaaaaaa";
    private static final String PERIODO_ENCERRADO = "aaaaaaaa-0003-0003-0003-aaaaaaaaaaaa";

    @Autowired
    private RegistrarFatoContabil registrarFatoContabil;

    @Autowired
    private EstornarFatoContabil estornarFatoContabil;

    @BeforeAll
    static void migraCriaLoginDeMenorPrivilegioESemeiaPeriodos() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection dono = adminConnection();
                Statement st = dono.createStatement()) {
            st.execute("create role app_login login password 'app_login'");
            st.execute("grant app_role to app_login");
            st.execute("""
                    insert into ente (id, cnpj, nome, esfera) values ('%s', '99999999999999', 'Ente RAZ-27', 'municipio')
                    """.formatted(ENTE));
            st.execute("""
                    insert into periodo_contabil (id, ente_id, exercicio, mes, status) values
                      ('%s', '%s', 2026, 1, 'aberto'),
                      ('%s', '%s', 2026, 2, 'aberto'),
                      ('%s', '%s', 2026, 3, 'encerrado')
                    """.formatted(PERIODO_JANEIRO, ENTE, PERIODO_FEVEREIRO, ENTE, PERIODO_ENCERRADO, ENTE));
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
    // 1. Fluxo feliz: registrar -> estornar através do pipeline real (use
    //    case + adapters razao-infra), batendo no banco de verdade.
    // ------------------------------------------------------------------

    @Test
    void registrarEEstornarFuncionamFimAFimAtravesDoPipelineRealContraPostgres() throws SQLException {
        TenantId enteId = TenantId.de(ENTE);
        UUID contaDebito = criarConta("RAZ27-1");
        UUID contaCredito = criarConta("RAZ27-2");

        FatoContabil empenho = registrarFatoContabil.executar(
                sessaoAutenticada(enteId),
                enteId,
                LocalDate.of(2026, 1, 15),
                TipoEvento.EMPENHO,
                "empenho via pipeline real (RAZ-27)",
                "test",
                List.of(
                        Lancamento.de(new ContaContabilId(contaDebito), Natureza.DEBITO, Dinheiro.de("1000.00")),
                        Lancamento.de(new ContaContabilId(contaCredito), Natureza.CREDITO, Dinheiro.de("1000.00"))));

        assertThat(saldoDevedorLiquidoViaRls(enteId, contaDebito))
                .as("debito refletido no saldo derivado sob RLS apos o registro pelo pipeline real")
                .isEqualByComparingTo("1000.00");

        FatoContabil estorno = estornarFatoContabil.executar(
                sessaoAutenticada(enteId),
                enteId,
                empenho.id(),
                LocalDate.of(2026, 1, 20),
                "estorno via pipeline real (RAZ-27)",
                "test");

        assertThat(estorno.isEstorno()).isTrue();
        assertThat(estorno.fatoEstornadoId()).isEqualTo(empenho.id());
        assertThat(saldoDevedorLiquidoViaRls(enteId, contaDebito))
                .as("estorno neutraliza o saldo - o fato original nao e alterado, so compensado por um novo fato")
                .isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------
    // 2. RegistrarFatoContabil deriva periodoId via PeriodoContabilPort
    //    .periodoAbertoPara(dataCompetencia) - nao aceita periodoId explicito
    //    (ao contrario do golden set, que insere o fato direto via SQL cru).
    // ------------------------------------------------------------------

    @Test
    void derivaOPeriodoAbertoDaDataDeCompetenciaEmVezDeReceberIdExplicito() throws SQLException {
        TenantId enteId = TenantId.de(ENTE);
        UUID contaDebito = criarConta("RAZ27-3");
        UUID contaCredito = criarConta("RAZ27-4");

        FatoContabil fatoJaneiro = registrarFatoContabil.executar(
                sessaoAutenticada(enteId),
                enteId,
                LocalDate.of(2026, 1, 10),
                TipoEvento.RECEITA,
                "competencia janeiro",
                "test",
                List.of(
                        Lancamento.de(new ContaContabilId(contaDebito), Natureza.DEBITO, Dinheiro.de("50.00")),
                        Lancamento.de(new ContaContabilId(contaCredito), Natureza.CREDITO, Dinheiro.de("50.00"))));
        FatoContabil fatoFevereiro = registrarFatoContabil.executar(
                sessaoAutenticada(enteId),
                enteId,
                LocalDate.of(2026, 2, 10),
                TipoEvento.RECEITA,
                "competencia fevereiro",
                "test",
                List.of(
                        Lancamento.de(new ContaContabilId(contaDebito), Natureza.DEBITO, Dinheiro.de("70.00")),
                        Lancamento.de(new ContaContabilId(contaCredito), Natureza.CREDITO, Dinheiro.de("70.00"))));

        assertThat(fatoJaneiro.periodoId())
                .as("10/01 cai no periodo de janeiro - derivado da data de competencia, nunca recebido como argumento")
                .isEqualTo(new PeriodoContabilId(UUID.fromString(PERIODO_JANEIRO)));
        assertThat(fatoFevereiro.periodoId())
                .as("10/02 cai no periodo de fevereiro - mesmo ente e use case, periodo muda so pela data de competencia")
                .isEqualTo(new PeriodoContabilId(UUID.fromString(PERIODO_FEVEREIRO)));
    }

    // ------------------------------------------------------------------
    // 3. Falhas de dominio propagam como excecao de dominio, nunca
    //    SQLException cru, quando vem do caminho real da aplicacao.
    // ------------------------------------------------------------------

    @Test
    void partidasDesbalanceadasPropagaComoExcecaoDeDominioSemTocarOBanco() {
        TenantId enteId = TenantId.de(ENTE);
        List<Lancamento> desbalanceado = List.of(
                Lancamento.de(ContaContabilId.novo(), Natureza.DEBITO, Dinheiro.de("100.00")),
                Lancamento.de(ContaContabilId.novo(), Natureza.CREDITO, Dinheiro.de("40.00")));

        assertThatThrownBy(() -> registrarFatoContabil.executar(
                        sessaoAutenticada(enteId), enteId, LocalDate.of(2026, 1, 12), TipoEvento.EMPENHO,
                        "desbalanceado", "test", desbalanceado))
                .as("o pipeline real rejeita ANTES de qualquer I/O - excecao de dominio, nao um SQLException da trigger")
                .isInstanceOf(PartidasNaoBalanceadasException.class)
                .isNotInstanceOf(SQLException.class);
    }

    @Test
    void periodoEncerradoPropagaComoExcecaoDeDominioAntesDeQualquerInsertNoFatoContabil() throws SQLException {
        TenantId enteId = TenantId.de(ENTE);

        assertThatThrownBy(() -> registrarFatoContabil.executar(
                        sessaoAutenticada(enteId),
                        enteId,
                        LocalDate.of(2026, 3, 5),
                        TipoEvento.EMPENHO,
                        "tentativa em periodo encerrado via pipeline real",
                        "test",
                        List.of(
                                Lancamento.de(ContaContabilId.novo(), Natureza.DEBITO, Dinheiro.de("10.00")),
                                Lancamento.de(ContaContabilId.novo(), Natureza.CREDITO, Dinheiro.de("10.00")))))
                .as("PostgresPeriodoContabilPort rejeita antes do INSERT chegar ao banco - excecao de dominio, "
                        + "nao a mensagem crua da trigger de periodo encerrado (essa e a trava vista por "
                        + "RazaoContabilTravasTest, aqui via SQL cru)")
                .isInstanceOf(PeriodoEncerradoException.class)
                .isNotInstanceOf(SQLException.class);

        assertThat(contarFatosNoPeriodo(PERIODO_ENCERRADO))
                .as("nada foi persistido: a falha e detectada antes do INSERT, nao revertida depois dele")
                .isZero();
    }

    private static UUID criarConta(String codigo) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection dono = adminConnection();
                Statement st = dono.createStatement()) {
            st.execute(
                    """
                    insert into conta_pcasp (id, ente_id, codigo, descricao, natureza_informacao, natureza_saldo)
                    values ('%s', '%s', '%s', 'Conta %s', 'patrimonial', 'D')
                    """
                            .formatted(id, ENTE, codigo, codigo));
        }
        return id;
    }

    private static long contarFatosNoPeriodo(String periodoId) throws SQLException {
        try (Connection conn = adminConnection();
                Statement st = conn.createStatement();
                ResultSet rs =
                        st.executeQuery("select count(*) from fato_contabil where periodo_id = '" + periodoId + "'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

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
