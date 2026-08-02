package br.contabil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
import br.contabil.razao.application.GerarMatrizSaldosContabeis;
import br.contabil.razao.application.RegistrarFatoContabil;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.ExecucaoOrcamentaria;
import br.contabil.razao.domain.FonteRecurso;
import br.contabil.razao.domain.FuncaoSubfuncao;
import br.contabil.razao.domain.InformacoesComplementares;
import br.contabil.razao.domain.Lancamento;
import br.contabil.razao.domain.LinhaMatrizSaldos;
import br.contabil.razao.domain.Natureza;
import br.contabil.razao.domain.NaturezaDespesa;
import br.contabil.razao.domain.PoderOrgao;
import br.contabil.razao.domain.TipoEvento;

/**
 * RAZ-269 (ADR-0057 fase 4): prova que {@code MatrizSaldosContabeisPort}, através
 * do pipeline real ({@link GerarMatrizSaldosContabeis} + adapter
 * {@code PostgresMatrizSaldosContabeisPort}, contra Postgres via Testcontainers),
 * fecha nas duas invariantes descritas no escopo da issue: (1) Σdébito=Σcrédito
 * somando TODAS as linhas devolvidas — cada fato registrado já é balanceado
 * (Σ=Σ do razão), então a soma da projeção herda o mesmo fechamento; (2) o saldo
 * anterior de um período é o saldo atual acumulado do período imediatamente
 * anterior para a MESMA combinação conta×IC — não apenas por conta, como o
 * balancete simples.
 */
@SpringBootTest(classes = RazaoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(MatrizSaldosContabeisIntegrationTest.ServicoIdentidadePermissivoParaTeste.class)
class MatrizSaldosContabeisIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

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

    private static final String ENTE = "88888888-8888-8888-8888-888888888888";
    private static final String PERIODO_JANEIRO = "bbbbbbbb-0001-0001-0001-bbbbbbbbbbbb";
    private static final String PERIODO_FEVEREIRO = "bbbbbbbb-0002-0002-0002-bbbbbbbbbbbb";

    @Autowired
    private RegistrarFatoContabil registrarFatoContabil;

    @Autowired
    private GerarMatrizSaldosContabeis gerarMatrizSaldosContabeis;

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
                    insert into ente (id, cnpj, nome, esfera) values ('%s', '88888888888888', 'Ente RAZ-269', 'municipio')
                    """.formatted(ENTE));
            st.execute("""
                    insert into periodo_contabil (id, ente_id, exercicio, mes, status) values
                      ('%s', '%s', 2026, 1, 'aberto'),
                      ('%s', '%s', 2026, 2, 'aberto')
                    """.formatted(PERIODO_JANEIRO, ENTE, PERIODO_FEVEREIRO, ENTE));
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

    @Test
    void projecaoFechaSomaDebitoIgualSomaCreditoEAgrupaPorContaEIc() throws SQLException {
        TenantId enteId = TenantId.de(ENTE);
        UUID contaDespesa = criarConta("RAZ269-1");
        UUID contaDisponibilidades = criarConta("RAZ269-2");
        PoderOrgao poderOrgao = PoderOrgao.de("01101");
        InformacoesComplementares icDespesa = InformacoesComplementares.de(
                FonteRecurso.de("1500"),
                ExecucaoOrcamentaria.de("2"),
                null,
                NaturezaDespesa.de("33903000"),
                FuncaoSubfuncao.de("04122"),
                null);

        // Dois fatos no MESMO período (janeiro), MESMA combinação conta×IC — a
        // projeção deve somar os dois no mesmo grupo, não devolver duas linhas.
        registrarFatoContabil.executar(
                sessaoAutenticada(enteId), enteId, LocalDate.of(2026, 1, 10), TipoEvento.EMPENHO,
                "despesa 1 com IC (RAZ-269)", "test", poderOrgao,
                List.of(
                        Lancamento.de(new ContaContabilId(contaDespesa), Natureza.DEBITO, Dinheiro.de("300.00"), icDespesa),
                        Lancamento.de(new ContaContabilId(contaDisponibilidades), Natureza.CREDITO, Dinheiro.de("300.00"))));
        registrarFatoContabil.executar(
                sessaoAutenticada(enteId), enteId, LocalDate.of(2026, 1, 20), TipoEvento.EMPENHO,
                "despesa 2 com a mesma IC (RAZ-269)", "test", poderOrgao,
                List.of(
                        Lancamento.de(new ContaContabilId(contaDespesa), Natureza.DEBITO, Dinheiro.de("120.00"), icDespesa),
                        Lancamento.de(new ContaContabilId(contaDisponibilidades), Natureza.CREDITO, Dinheiro.de("120.00"))));

        List<LinhaMatrizSaldos> matrizJaneiro =
                gerarMatrizSaldosContabeis.executar(sessaoAutenticada(enteId), enteId, 2026, 1);

        // (1) Σdébito = Σcrédito somando toda a matriz do período — cada fato
        // registrado já é balanceado no razão; a projeção herda o fechamento.
        Dinheiro totalDebito = matrizJaneiro.stream().map(LinhaMatrizSaldos::movimentoDebito).reduce(Dinheiro.zero(), Dinheiro::somar);
        Dinheiro totalCredito = matrizJaneiro.stream().map(LinhaMatrizSaldos::movimentoCredito).reduce(Dinheiro.zero(), Dinheiro::somar);
        assertThat(totalDebito).as("MSC fecha: soma dos movimentos a debito = soma dos movimentos a credito").isEqualTo(totalCredito);
        assertThat(totalDebito).isEqualTo(Dinheiro.de("420.00"));

        // (2) a conta de despesa com IC agrupou os DOIS fatos numa única linha
        // (mesma conta x mesma combinacao IC x mesmo periodo).
        LinhaMatrizSaldos linhaDespesa = linhaDe(matrizJaneiro, contaDespesa, icDespesa);
        assertThat(linhaDespesa.movimentoDebito()).isEqualTo(Dinheiro.de("420.00"));
        assertThat(linhaDespesa.movimentoCredito()).isEqualTo(Dinheiro.zero());
        assertThat(linhaDespesa.saldoAnterior()).isEqualTo(Dinheiro.zero());
        assertThat(linhaDespesa.saldoAtual()).isEqualTo(Dinheiro.de("420.00"));
        assertThat(linhaDespesa.poderOrgao()).isEqualTo(poderOrgao);
        assertThat(linhaDespesa.ic()).isEqualTo(icDespesa);

        // a conta de disponibilidades nao carrega IC de partida (so o credito, sem
        // ic) mas AINDA carrega o PO (fato-wide) - dimensao distinta por natureza.
        LinhaMatrizSaldos linhaDisponibilidades =
                linhaDe(matrizJaneiro, contaDisponibilidades, InformacoesComplementares.nenhuma());
        assertThat(linhaDisponibilidades.movimentoCredito()).isEqualTo(Dinheiro.de("420.00"));
        assertThat(linhaDisponibilidades.poderOrgao()).isEqualTo(poderOrgao);

        // (3) fevereiro: um novo fato na MESMA combinacao conta x IC deve herdar o
        // saldo anterior acumulado de janeiro para aquela combinacao especifica -
        // nao so por conta (e o que estende o balancete simples pela dimensao IC).
        registrarFatoContabil.executar(
                sessaoAutenticada(enteId), enteId, LocalDate.of(2026, 2, 5), TipoEvento.EMPENHO,
                "despesa 3 em fevereiro, mesma IC (RAZ-269)", "test", poderOrgao,
                List.of(
                        Lancamento.de(new ContaContabilId(contaDespesa), Natureza.DEBITO, Dinheiro.de("80.00"), icDespesa),
                        Lancamento.de(new ContaContabilId(contaDisponibilidades), Natureza.CREDITO, Dinheiro.de("80.00"))));

        List<LinhaMatrizSaldos> matrizFevereiro =
                gerarMatrizSaldosContabeis.executar(sessaoAutenticada(enteId), enteId, 2026, 2);
        LinhaMatrizSaldos linhaDespesaFevereiro = linhaDe(matrizFevereiro, contaDespesa, icDespesa);
        assertThat(linhaDespesaFevereiro.saldoAnterior())
                .as("saldo anterior de fevereiro para conta x IC = saldo atual acumulado de janeiro para a MESMA combinacao")
                .isEqualTo(Dinheiro.de("420.00"));
        assertThat(linhaDespesaFevereiro.movimentoDebito()).isEqualTo(Dinheiro.de("80.00"));
        assertThat(linhaDespesaFevereiro.saldoAtual()).isEqualTo(Dinheiro.de("500.00"));

        Dinheiro totalDebitoFevereiro =
                matrizFevereiro.stream().map(LinhaMatrizSaldos::movimentoDebito).reduce(Dinheiro.zero(), Dinheiro::somar);
        Dinheiro totalCreditoFevereiro =
                matrizFevereiro.stream().map(LinhaMatrizSaldos::movimentoCredito).reduce(Dinheiro.zero(), Dinheiro::somar);
        assertThat(totalDebitoFevereiro).isEqualTo(totalCreditoFevereiro);
    }

    private static LinhaMatrizSaldos linhaDe(
            List<LinhaMatrizSaldos> matriz, UUID contaId, InformacoesComplementares ic) {
        return matriz.stream()
                .filter(linha -> linha.contaId().valor().equals(contaId) && linha.ic().equals(ic))
                .findFirst()
                .orElseThrow(() -> new AssertionError("linha nao encontrada para conta=%s ic=%s em %s"
                        .formatted(contaId, ic, matriz)));
    }

    private static UUID criarConta(String codigo) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection dono = adminConnection();
                Statement st = dono.createStatement()) {
            st.execute(
                    """
                    insert into conta_pcasp (id, ente_id, codigo, descricao, natureza_informacao, natureza_saldo, escrituravel)
                    values ('%s', '%s', '%s', 'Conta %s', 'orcamentaria', 'D', true)
                    """
                            .formatted(id, ENTE, codigo, codigo));
        }
        return id;
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
