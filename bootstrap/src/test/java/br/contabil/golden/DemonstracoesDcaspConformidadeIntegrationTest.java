package br.contabil.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.DemonstracoesDcasp;
import br.contabil.razao.domain.LinhaDemonstracaoDcasp;
import br.contabil.razao.infra.PostgresDemonstracoesDcaspPort;

/**
 * Prova o adapter Postgres das demonstrações DCASP (RAZ-241/ADR-0047) contra o
 * schema real: read model derivado do razão, tenant-scoped por RLS e sem
 * snapshot materializado.
 */
@Testcontainers(disabledWithoutDocker = true)
class DemonstracoesDcaspConformidadeIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void preparaBase() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection admin = adminConnection();
                Statement st = admin.createStatement()) {
            st.execute("create role app_login login password 'app_login'");
            st.execute("grant app_role to app_login");
        }
    }

    @Test
    @DisplayName("deriva as quatro demonstrações do razão por exercício e não vaza outro ente")
    void derivaQuatroDemonstracoesDoRazaoPorExercicioETenant() throws SQLException {
        String enteA = criarEnte("55555555555501", "Ente DCASP A");
        String enteB = criarEnte("55555555555502", "Ente DCASP B");

        DadosDcasp dadosA = prepararDadosDcasp(enteA, "1000.00", "300.00", "200.00", "50.00");
        prepararDadosDcasp(enteB, "999.00", "999.00", "999.00", "999.00");

        DemonstracoesDcasp demonstracoes = consultarDcasp(enteA, enteA, 2026);

        assertThat(linha(demonstracoes.balancoOrcamentario().linhas(), dadosA.creditoDisponivelCodigo())
                        .valores()
                        .get("exercicioAtual"))
                .as("conta orçamentária credora é normalizada pela natureza da conta")
                .isEqualTo(Dinheiro.de("1000.00"));
        assertThat(linha(demonstracoes.balancoOrcamentario().linhas(), dadosA.empenhadoCodigo())
                        .valores()
                        .get("movimentoDebito"))
                .isEqualTo(Dinheiro.de("1000.00"));

        assertThat(linha(demonstracoes.balancoFinanceiro().linhas(), dadosA.caixaCodigo())
                        .valores()
                        .get("exercicioAtual"))
                .as("Balanço Financeiro usa saldo acumulado até o exercício")
                .isEqualTo(Dinheiro.de("250.00"));
        assertThat(linha(demonstracoes.balancoFinanceiro().linhas(), dadosA.caixaCodigo())
                        .valores()
                        .get("exercicioAnterior"))
                .isEqualTo(Dinheiro.de("50.00"));

        assertThat(linha(demonstracoes.balancoPatrimonial().linhas(), dadosA.fornecedoresCodigo())
                        .valores()
                        .get("exercicioAtual"))
                .isEqualTo(Dinheiro.de("300.00"));
        assertThat(linha(demonstracoes.dvp().linhas(), dadosA.vpdCodigo()).valores().get("exercicioAtual"))
                .isEqualTo(Dinheiro.de("300.00"));
        assertThat(linha(demonstracoes.dvp().linhas(), dadosA.vpaCodigo()).valores().get("exercicioAtual"))
                .isEqualTo(Dinheiro.de("200.00"));

        DemonstracoesDcasp outroEnteVistoDaSessaoA = consultarDcasp(enteA, enteB, 2026);
        assertThat(outroEnteVistoDaSessaoA.balancoOrcamentario().linhas()).isEmpty();
        assertThat(outroEnteVistoDaSessaoA.balancoFinanceiro().linhas()).isEmpty();
        assertThat(outroEnteVistoDaSessaoA.balancoPatrimonial().linhas()).isEmpty();
        assertThat(outroEnteVistoDaSessaoA.dvp().linhas()).isEmpty();
    }

    private record DadosDcasp(
            String caixaCodigo,
            String fornecedoresCodigo,
            String vpdCodigo,
            String vpaCodigo,
            String creditoDisponivelCodigo,
            String empenhadoCodigo) {
    }

    private record Partida(String contaId, char natureza, String valor) {
    }

    private static DadosDcasp prepararDadosDcasp(
            String enteId, String orcamento, String vpd, String receita, String saldoInicial) throws SQLException {
        String caixaCodigo = "1.1.1." + sufixo(enteId);
        String fornecedoresCodigo = "2.1.3." + sufixo(enteId);
        String patrimonioCodigo = "2.3.7." + sufixo(enteId);
        String vpdCodigo = "3.3.1." + sufixo(enteId);
        String vpaCodigo = "4.1.1." + sufixo(enteId);
        String creditoDisponivelCodigo = "5.2.2." + sufixo(enteId);
        String empenhadoCodigo = "6.2.2." + sufixo(enteId);

        String caixa = criarConta(enteId, caixaCodigo, "Caixa e equivalentes", "patrimonial", "D");
        String fornecedores = criarConta(enteId, fornecedoresCodigo, "Fornecedores a pagar", "patrimonial", "C");
        String patrimonio = criarConta(enteId, patrimonioCodigo, "Resultado acumulado", "patrimonial", "C");
        String vpdConta = criarConta(enteId, vpdCodigo, "VPD corrente", "patrimonial", "D");
        String vpaConta = criarConta(enteId, vpaCodigo, "VPA corrente", "patrimonial", "C");
        String creditoDisponivel =
                criarConta(enteId, creditoDisponivelCodigo, "Crédito disponível", "orcamentaria", "C");
        String empenhadoConta =
                criarConta(enteId, empenhadoCodigo, "Crédito empenhado a liquidar", "orcamentaria", "D");

        String periodoAnterior = criarPeriodo(enteId, 2025, 12);
        registrarFato(
                enteId, periodoAnterior, "abertura", debito(caixa, saldoInicial), credito(patrimonio, saldoInicial));

        String periodoAtual = criarPeriodo(enteId, 2026, 1);
        registrarFato(
                enteId,
                periodoAtual,
                "empenho",
                debito(empenhadoConta, orcamento),
                credito(creditoDisponivel, orcamento));
        registrarFato(enteId, periodoAtual, "liquidacao", debito(vpdConta, vpd), credito(fornecedores, vpd));
        registrarFato(enteId, periodoAtual, "receita", debito(caixa, receita), credito(vpaConta, receita));

        return new DadosDcasp(
                caixaCodigo, fornecedoresCodigo, vpdCodigo, vpaCodigo, creditoDisponivelCodigo, empenhadoCodigo);
    }

    private static String sufixo(String enteId) {
        return enteId.substring(0, 4);
    }

    private static Partida debito(String contaId, String valor) {
        return new Partida(contaId, 'D', valor);
    }

    private static Partida credito(String contaId, String valor) {
        return new Partida(contaId, 'C', valor);
    }

    @FunctionalInterface
    private interface OperacaoAppLogin<T> {
        T executar(Statement st) throws SQLException;
    }

    private static <T> T comoAppLogin(String enteSessao, OperacaoAppLogin<T> operacao) throws SQLException {
        try (Connection conn = appLoginConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("set local app.ente_id = '" + enteSessao + "'");
                T resultado = operacao.executar(st);
                conn.commit();
                return resultado;
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection appLoginConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
    }

    private static String criarEnte(String cnpj, String nome) throws SQLException {
        try (Connection admin = adminConnection();
                Statement st = admin.createStatement();
                ResultSet rs = st.executeQuery(
                        "insert into ente (cnpj, nome, esfera) values ('%s','%s','municipio') returning id"
                                .formatted(cnpj, nome))) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static String criarConta(
            String enteId, String codigo, String descricao, String naturezaInformacao, String naturezaSaldo)
            throws SQLException {
        return comoAppLogin(enteId, st -> {
            try (ResultSet rs = st.executeQuery(
                    ("insert into conta_pcasp (ente_id, codigo, descricao, natureza_informacao, natureza_saldo) "
                                    + "values ('%s','%s','%s','%s','%s') returning id")
                            .formatted(enteId, codigo, descricao, naturezaInformacao, naturezaSaldo))) {
                rs.next();
                return rs.getString(1);
            }
        });
    }

    private static String criarPeriodo(String enteId, int exercicio, int mes) throws SQLException {
        return comoAppLogin(enteId, st -> {
            try (ResultSet rs = st.executeQuery(
                    "insert into periodo_contabil (ente_id, exercicio, mes, status) values ('%s',%d,%d,'aberto') returning id"
                            .formatted(enteId, exercicio, mes))) {
                rs.next();
                return rs.getString(1);
            }
        });
    }

    private static String registrarFato(String enteId, String periodoId, String tipoEvento, Partida... partidas)
            throws SQLException {
        return comoAppLogin(enteId, st -> {
            long numeroSeq;
            try (ResultSet rs = st.executeQuery("select proximo_numero_seq()")) {
                rs.next();
                numeroSeq = rs.getLong(1);
            }
            String fatoId = UUID.randomUUID().toString();
            st.execute("""
                    insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem)
                    values ('%s', '%s', %d, current_date, '%s', '%s', 'golden-set-dcasp', 'golden-set-dcasp')
                    """.formatted(fatoId, enteId, numeroSeq, periodoId, tipoEvento));
            for (Partida p : partidas) {
                st.execute("insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s','%s','%s','%s',%s)"
                        .formatted(enteId, fatoId, p.contaId(), p.natureza(), p.valor()));
            }
            return fatoId;
        });
    }

    private static DemonstracoesDcasp consultarDcasp(String enteSessao, String enteConsulta, int exercicio)
            throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            try (Statement st = conexao.createStatement()) {
                st.execute("set local app.ente_id = '" + enteSessao + "'");
            }
            PostgresDemonstracoesDcaspPort port =
                    new PostgresDemonstracoesDcaspPort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));
            DemonstracoesDcasp resultado = port.demonstracoes(TenantId.de(enteConsulta), exercicio);
            conexao.commit();
            return resultado;
        }
    }

    private static LinhaDemonstracaoDcasp linha(List<LinhaDemonstracaoDcasp> linhas, String codigo) {
        return linhas.stream().filter(l -> l.codigo().equals(codigo)).findFirst().orElse(null);
    }
}
