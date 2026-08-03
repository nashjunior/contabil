package br.contabil.golden;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
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
import br.contabil.razao.domain.LinhaMatrizSaldosContabeis;
import br.contabil.razao.domain.LinhaMatrizSaldosContabeisDerivada;
import br.contabil.razao.domain.MatrizSaldosContabeis;
import br.contabil.razao.infra.PostgresMatrizSaldosContabeisPort;

/**
 * Conformidade da Matriz de Saldos Contábeis (RAZ-269) contra Postgres real (Testcontainers)
 * — extensão do gold set de {@link BalanceteConformidadeIntegrationTest} (mesma metodologia)
 * pela dimensão das informações complementares (IC, ADR-0056/ADR-0057): prova que
 * {@link PostgresMatrizSaldosContabeisPort} agrupa {@code conta × IC} corretamente (uma
 * combinação por linha, não uma reclassificação/heurística), que o {@code FP} derivado
 * (superávit financeiro) bate com a soma das linhas capturadas da DDR de origem por fonte, e
 * que a matriz fecha (Σdébito=Σcrédito) tanto no agregado quanto por círculo de partidas
 * dobradas independente (patrimonial × controle/DDR — PCASP segrega as classes em circuitos
 * de débito/crédito próprios, não um único Σ=Σ misturando patrimonial com DDR).
 */
@Testcontainers(disabledWithoutDocker = true)
class MatrizSaldosContabeisConformidadeIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static String enteA;

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

        enteA = criarEnte("44444444444501", "Ente MSC A");
    }

    // ------------------------------------------------------------------
    // 1. Uma mesma conta com/sem IC de partida (FR/CO) e com/sem PO de fato
    //    vira LINHAS SEPARADAS na matriz, cada uma com seu próprio saldo
    //    anterior/movimento/saldo atual — a extensão do balancete pela IC.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("linhas da matriz separam por combinação conta×IC (FR/CO) e por Poder/Órgão do fato")
    void linhasDaMatrizSeparamPorCombinacaoContaIcEPoderOrgao() throws SQLException {
        String caixa = criarConta(enteA, "MSCA-1.1.1", "Caixa", "patrimonial", "D", true);
        String fornecedores = criarConta(enteA, "MSCA-2.1.3", "Fornecedores a Pagar", "patrimonial", "C", true);

        String periodoAnterior = criarPeriodo(enteA, 2027, 1);
        registrarFato(enteA, periodoAnterior, "abertura", "saldo inicial sem IC", null, null,
                debito(caixa, "500.00"), credito(fornecedores, "500.00"));

        String periodoAlvo = criarPeriodo(enteA, 2027, 2);
        registrarFato(enteA, periodoAlvo, "empenho", "movimento com IC (FR/CO) e PO", null, "ORGAO-1",
                debitoComIc(caixa, "300.00", "FONTE-A", "CO-X"),
                creditoComIc(fornecedores, "300.00", "FONTE-A", "CO-X"));
        registrarFato(enteA, periodoAlvo, "pagamento", "movimento sem IC", null, null,
                debito(caixa, "100.00"), credito(fornecedores, "100.00"));

        MatrizSaldosContabeis matriz = consultarMatriz(enteA, enteA, 2027, 2);

        assertThat(matriz.linhas()).hasSize(4);

        LinhaMatrizSaldosContabeis caixaSemIc = linha(matriz, "MSCA-1.1.1", null);
        assertThat(caixaSemIc.saldoAnterior()).isEqualTo(Dinheiro.de("500.00"));
        assertThat(caixaSemIc.movimentoDebito()).isEqualTo(Dinheiro.de("100.00"));
        assertThat(caixaSemIc.movimentoCredito()).isEqualTo(Dinheiro.zero());
        assertThat(caixaSemIc.saldoAtual()).isEqualTo(Dinheiro.de("600.00"));
        assertThat(caixaSemIc.poderOrgao()).isNull();
        assertThat(caixaSemIc.informacoesComplementares().vazia()).isTrue();

        LinhaMatrizSaldosContabeis caixaComIc = linha(matriz, "MSCA-1.1.1", "FONTE-A");
        assertThat(caixaComIc.saldoAnterior())
                .as("nenhum lançamento anterior carregava esta combinação de IC")
                .isEqualTo(Dinheiro.zero());
        assertThat(caixaComIc.movimentoDebito()).isEqualTo(Dinheiro.de("300.00"));
        assertThat(caixaComIc.saldoAtual()).isEqualTo(Dinheiro.de("300.00"));
        assertThat(caixaComIc.informacoesComplementares().fonteRecurso().codigo()).isEqualTo("FONTE-A");
        assertThat(caixaComIc.informacoesComplementares().execucaoOrcamentaria().codigo()).isEqualTo("CO-X");
        assertThat(caixaComIc.poderOrgao().codigo()).isEqualTo("ORGAO-1");

        LinhaMatrizSaldosContabeis fornecedoresSemIc = linha(matriz, "MSCA-2.1.3", null);
        assertThat(fornecedoresSemIc.saldoAnterior()).isEqualTo(Dinheiro.de("-500.00"));
        assertThat(fornecedoresSemIc.movimentoCredito()).isEqualTo(Dinheiro.de("100.00"));
        assertThat(fornecedoresSemIc.saldoAtual()).isEqualTo(Dinheiro.de("-600.00"));

        LinhaMatrizSaldosContabeis fornecedoresComIc = linha(matriz, "MSCA-2.1.3", "FONTE-A");
        assertThat(fornecedoresComIc.movimentoCredito()).isEqualTo(Dinheiro.de("300.00"));
        assertThat(fornecedoresComIc.saldoAtual()).isEqualTo(Dinheiro.de("-300.00"));

        assertThat(matriz.totalMovimentoDebito()).isEqualTo(Dinheiro.de("400.00"));
        assertThat(matriz.totalMovimentoCredito()).isEqualTo(Dinheiro.de("400.00"));
        assertThat(matriz.confere()).isTrue();
    }

    // ------------------------------------------------------------------
    // 2. FP (superávit financeiro) é DERIVADO da DDR "Recursos Disponíveis"
    //    (8.2.1.1.1.01/.02.00) por fonte — bate com a soma das linhas
    //    CAPTURADAS dessas contas para a mesma fonte (duas queries SQL
    //    independentes chegando ao mesmo número). A matriz fecha tanto no
    //    agregado quanto por circuito de partidas dobradas (patrimonial ×
    //    controle/DDR não se misturam).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FP derivado bate com a soma das linhas capturadas da DDR por fonte; fecha por circuito patrimonial × controle")
    void fpDerivadoBateComDdrCapturadaEMatrizFechaPorCircuito() throws SQLException {
        String caixa = criarConta(enteA, "MSCB-1.1.1", "Caixa", "patrimonial", "D", true);
        String fornecedores = criarConta(enteA, "MSCB-2.1.3", "Fornecedores a Pagar", "patrimonial", "C", true);
        String controle = criarConta(enteA, "MSCB-7.2.1", "Controle da Disponibilidade (teste)", "controle", "D", true);
        String recursosExercicio = criarConta(enteA, "8.2.1.1.1.01.00", "Recursos Disponíveis Exercício", "controle", "C", true);
        String recursosAnteriores = criarConta(enteA, "8.2.1.1.1.02.00", "Recursos de Exercícios Anteriores", "controle", "C", true);

        String periodo = criarPeriodo(enteA, 2027, 5);

        registrarFato(enteA, periodo, "pagamento", "movimento patrimonial sem IC/DDR", null, null,
                debito(caixa, "700.00"), credito(fornecedores, "700.00"));
        registrarFato(enteA, periodo, "empenho", "DDR fonte B — recursos do exercício", null, null,
                debitoComFonte(controle, "150.00", "FONTE-B"), creditoComFonte(recursosExercicio, "150.00", "FONTE-B"));
        registrarFato(enteA, periodo, "empenho", "DDR fonte B — recursos de exercícios anteriores", null, null,
                debitoComFonte(controle, "80.00", "FONTE-B"), creditoComFonte(recursosAnteriores, "80.00", "FONTE-B"));
        registrarFato(enteA, periodo, "empenho", "DDR fonte C — recursos do exercício", null, null,
                debitoComFonte(controle, "40.00", "FONTE-C"), creditoComFonte(recursosExercicio, "40.00", "FONTE-C"));

        MatrizSaldosContabeis matriz = consultarMatriz(enteA, enteA, 2027, 5);

        // --- fecha no agregado (Σdébito=Σcrédito da matriz inteira) ---
        assertThat(matriz.confere()).isTrue();
        assertThat(matriz.totalMovimentoDebito()).isEqualTo(Dinheiro.de("970.00"));
        assertThat(matriz.totalMovimentoCredito()).isEqualTo(Dinheiro.de("970.00"));

        // --- fecha por circuito: patrimonial (1.1.1/2.1.3) não se mistura com controle/DDR (7.2.1/8.2.1.1.1.xx) ---
        LinhaMatrizSaldosContabeis caixaLinha = linha(matriz, "MSCB-1.1.1", null);
        LinhaMatrizSaldosContabeis fornecedoresLinha = linha(matriz, "MSCB-2.1.3", null);
        Dinheiro debitoPatrimonial = caixaLinha.movimentoDebito();
        Dinheiro creditoPatrimonial = fornecedoresLinha.movimentoCredito();
        assertThat(debitoPatrimonial)
                .as("circuito patrimonial fecha isoladamente do circuito controle/DDR")
                .isEqualTo(creditoPatrimonial)
                .isEqualTo(Dinheiro.de("700.00"));

        LinhaMatrizSaldosContabeis controleFonteB = linha(matriz, "MSCB-7.2.1", "FONTE-B");
        LinhaMatrizSaldosContabeis controleFonteC = linha(matriz, "MSCB-7.2.1", "FONTE-C");
        LinhaMatrizSaldosContabeis recursosExercicioFonteB = linha(matriz, "8.2.1.1.1.01.00", "FONTE-B");
        LinhaMatrizSaldosContabeis recursosAnterioresFonteB = linha(matriz, "8.2.1.1.1.02.00", "FONTE-B");
        LinhaMatrizSaldosContabeis recursosExercicioFonteC = linha(matriz, "8.2.1.1.1.01.00", "FONTE-C");

        Dinheiro debitoControle = controleFonteB.movimentoDebito().somar(controleFonteC.movimentoDebito());
        Dinheiro creditoControle = recursosExercicioFonteB
                .movimentoCredito()
                .somar(recursosAnterioresFonteB.movimentoCredito())
                .somar(recursosExercicioFonteC.movimentoCredito());
        assertThat(debitoControle)
                .as("circuito controle/DDR fecha isoladamente do circuito patrimonial")
                .isEqualTo(creditoControle)
                .isEqualTo(Dinheiro.de("270.00"));

        // --- FP derivado = soma das linhas capturadas da DDR "Recursos Disponíveis" por fonte ---
        assertThat(matriz.linhasDerivadas()).hasSize(2);

        LinhaMatrizSaldosContabeisDerivada fpFonteB = derivada(matriz, "FONTE-B");
        Dinheiro fpEsperadoFonteB = recursosExercicioFonteB.saldoAtual().somar(recursosAnterioresFonteB.saldoAtual());
        assertThat(fpFonteB.saldoAtual())
                .as("FP da fonte B == soma das linhas CAPTURADAS de 8.2.1.1.1.01/.02.00 para a fonte B")
                .isEqualTo(fpEsperadoFonteB)
                .isEqualTo(Dinheiro.de("-230.00"));

        LinhaMatrizSaldosContabeisDerivada fpFonteC = derivada(matriz, "FONTE-C");
        assertThat(fpFonteC.saldoAtual())
                .as("FP da fonte C == saldo capturado de 8.2.1.1.1.01.00 para a fonte C (fonte C não tem .02.00)")
                .isEqualTo(recursosExercicioFonteC.saldoAtual())
                .isEqualTo(Dinheiro.de("-40.00"));

        // linha derivada é rótulo, não fato novo: não entra em confere()/totalMovimento*
        assertThat(matriz.totalMovimentoDebito())
                .as("linhas derivadas (FP) não duplicam o movimento já contado nas linhas capturadas da DDR")
                .isEqualTo(Dinheiro.de("970.00"));
    }

    // ------------------------------------------------------------------
    // 3. RLS: a matriz de um ente nunca inclui contas/fatos de outro, mesmo
    //    que o parâmetro de consulta divirja da sessão.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("matriz nunca vaza dados de outro ente, mesmo com enteId de consulta divergente da sessão RLS")
    void matrizRespeitaIsolamentoMultiTenantMesmoComEnteIdDivergenteDaSessao() throws SQLException {
        String enteC = criarEnte("44444444444503", "Ente MSC C (RLS)");
        String enteD = criarEnte("44444444444504", "Ente MSC D (RLS)");

        String caixaC = criarConta(enteC, "MSCC-1.1.1", "Caixa", "patrimonial", "D", true);
        String fornecedoresC = criarConta(enteC, "MSCC-2.1.3", "Fornecedores a Pagar", "patrimonial", "C", true);
        String periodoC = criarPeriodo(enteC, 2027, 1);
        registrarFato(enteC, periodoC, "empenho", "movimento do ente C", null, "ORGAO-C",
                debitoComFonte(caixaC, "900.00", "FONTE-C"), creditoComFonte(fornecedoresC, "900.00", "FONTE-C"));

        String caixaD = criarConta(enteD, "MSCC-1.1.1", "Caixa", "patrimonial", "D", true);
        String fornecedoresD = criarConta(enteD, "MSCC-2.1.3", "Fornecedores a Pagar", "patrimonial", "C", true);
        String periodoD = criarPeriodo(enteD, 2027, 1);
        registrarFato(enteD, periodoD, "empenho", "movimento do ente D", null, null,
                debito(caixaD, "777.00"), credito(fornecedoresD, "777.00"));

        MatrizSaldosContabeis matrizC = consultarMatriz(enteC, enteC, 2027, 1);
        assertThat(matrizC.linhas()).hasSize(2);
        assertThat(linha(matrizC, "MSCC-1.1.1", "FONTE-C").saldoAtual()).isEqualTo(Dinheiro.de("900.00"));

        MatrizSaldosContabeis matrizDVistoDeSessaoC = consultarMatriz(enteC, enteD, 2027, 1);
        assertThat(matrizDVistoDeSessaoC.linhas())
                .as("RLS bloqueia mesmo quando o parâmetro de consulta pede o ente D mas a sessão está setada para o ente C")
                .isEmpty();
        assertThat(matrizDVistoDeSessaoC.linhasDerivadas()).isEmpty();
    }

    // ------------------------------------------------------------------
    // 4. Exercício/mês sem período cadastrado resolve para matriz de linhas
    //    (capturadas e derivadas) vazias — não vaza dados de outro período.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("exercício/mês sem período cadastrado devolve matriz vazia, sem vazar dados de outro período")
    void periodoNaoCadastradoDevolveMatrizVazia() throws SQLException {
        String caixa = criarConta(enteA, "MSCD-1.1.1", "Caixa", "patrimonial", "D", true);
        String fornecedores = criarConta(enteA, "MSCD-2.1.3", "Fornecedores a Pagar", "patrimonial", "C", true);
        String periodoExistente = criarPeriodo(enteA, 2027, 8);
        registrarFato(enteA, periodoExistente, "empenho", "movimento em periodo existente (mes 8)", null, null,
                debito(caixa, "123.45"), credito(fornecedores, "123.45"));

        MatrizSaldosContabeis matriz = consultarMatriz(enteA, enteA, 2027, 9);

        assertThat(matriz.linhas()).as("mes 9 nunca foi cadastrado para este ente/exercício").isEmpty();
        assertThat(matriz.linhasDerivadas()).isEmpty();
        assertThat(matriz.confere()).isTrue();
    }

    // ------------------------------------------------------------------
    // Infraestrutura do teste
    // ------------------------------------------------------------------

    private record Partida(String contaId, char natureza, String valor, String fonteRecurso, String execucaoOrcamentaria) {
    }

    private static Partida debito(String contaId, String valor) {
        return new Partida(contaId, 'D', valor, null, null);
    }

    private static Partida credito(String contaId, String valor) {
        return new Partida(contaId, 'C', valor, null, null);
    }

    private static Partida debitoComFonte(String contaId, String valor, String fonteRecurso) {
        return new Partida(contaId, 'D', valor, fonteRecurso, null);
    }

    private static Partida creditoComFonte(String contaId, String valor, String fonteRecurso) {
        return new Partida(contaId, 'C', valor, fonteRecurso, null);
    }

    private static Partida debitoComIc(String contaId, String valor, String fonteRecurso, String execucaoOrcamentaria) {
        return new Partida(contaId, 'D', valor, fonteRecurso, execucaoOrcamentaria);
    }

    private static Partida creditoComIc(String contaId, String valor, String fonteRecurso, String execucaoOrcamentaria) {
        return new Partida(contaId, 'C', valor, fonteRecurso, execucaoOrcamentaria);
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

    private static String criarConta(String enteId, String codigo, String descricao, String naturezaInformacao,
            String naturezaSaldo, boolean escrituravel) throws SQLException {
        return comoAppLogin(enteId, st -> {
            try (ResultSet rs = st.executeQuery(
                    ("insert into conta_pcasp (ente_id, codigo, descricao, natureza_informacao, natureza_saldo, escrituravel) "
                            + "values ('%s','%s','%s','%s','%s',%b) returning id")
                            .formatted(enteId, codigo, descricao, naturezaInformacao, naturezaSaldo, escrituravel))) {
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

    private static String registrarFato(String enteId, String periodoId, String tipoEvento, String historico,
            String fatoEstornadoId, String poderOrgao, Partida... partidas) throws SQLException {
        return comoAppLogin(enteId, st -> {
            long numeroSeq;
            try (ResultSet rs = st.executeQuery("select proximo_numero_seq()")) {
                rs.next();
                numeroSeq = rs.getLong(1);
            }
            String fatoId = java.util.UUID.randomUUID().toString();
            st.execute("""
                    insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem, poder_orgao, fato_estornado_id)
                    values ('%s', '%s', %d, current_date, '%s', '%s', '%s', 'golden-set-msc', %s, %s)
                    """.formatted(fatoId, enteId, numeroSeq, periodoId, tipoEvento, historico,
                    poderOrgao == null ? "null" : "'" + poderOrgao + "'",
                    fatoEstornadoId == null ? "null" : "'" + fatoEstornadoId + "'"));
            for (Partida p : partidas) {
                st.execute("""
                        insert into lancamento (ente_id, fato_id, conta_id, natureza, valor, fonte_recurso, execucao_orcamentaria)
                        values ('%s','%s','%s','%s',%s,%s,%s)
                        """.formatted(enteId, fatoId, p.contaId(), p.natureza(), p.valor(),
                        p.fonteRecurso() == null ? "null" : "'" + p.fonteRecurso() + "'",
                        p.execucaoOrcamentaria() == null ? "null" : "'" + p.execucaoOrcamentaria() + "'"));
            }
            return fatoId;
        });
    }

    private static MatrizSaldosContabeis consultarMatriz(String enteSessao, String enteConsulta, int exercicio, int mes)
            throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            try (Statement st = conexao.createStatement()) {
                st.execute("set local app.ente_id = '" + enteSessao + "'");
            }
            PostgresMatrizSaldosContabeisPort port =
                    new PostgresMatrizSaldosContabeisPort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));
            MatrizSaldosContabeis resultado = port.matriz(TenantId.de(enteConsulta), exercicio, mes);
            conexao.commit();
            return resultado;
        }
    }

    private static LinhaMatrizSaldosContabeis linha(MatrizSaldosContabeis matriz, String codigo, String fonteRecurso) {
        return matriz.linhas().stream()
                .filter(l -> l.codigo().equals(codigo))
                .filter(l -> Objects.equals(fonteCodigo(l), fonteRecurso))
                .findFirst()
                .orElse(null);
    }

    private static String fonteCodigo(LinhaMatrizSaldosContabeis linha) {
        return linha.informacoesComplementares().fonteRecurso() == null
                ? null
                : linha.informacoesComplementares().fonteRecurso().codigo();
    }

    private static LinhaMatrizSaldosContabeisDerivada derivada(MatrizSaldosContabeis matriz, String fonteRecurso) {
        return matriz.linhasDerivadas().stream()
                .filter(l -> l.fonteRecurso().codigo().equals(fonteRecurso))
                .findFirst()
                .orElse(null);
    }
}
