package br.contabil.golden;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.LinhaBalancete;
import br.contabil.razao.infra.PostgresBalancetePort;

/**
 * Conformidade do balancete (RAZ-97) contra Postgres real (Testcontainers) — extensão do
 * gold set (RAZ-19/RAZ-84): prova que {@link PostgresBalancetePort} deriva saldo
 * anterior/movimento D/movimento C/saldo atual EXATAMENTE dos fatos do razão inseridos
 * (mesma metodologia de {@code GoldSetConformidadeContabilTest} — SQL direto autenticado
 * como {@code app_login}, travas do banco realmente ativas), cobrindo o que os testes de
 * unidade de {@code GerarBalancete}/{@code Balancete} não alcançam por serem mockados ou
 * construídos em memória: a SQL real de {@link PostgresBalancetePort} (filtro de conta
 * escriturável, corte de saldo anterior por período, o reflexo de estornos append-only no
 * movimento agregado) e o isolamento RLS entre entes.
 */
@Testcontainers(disabledWithoutDocker = true)
class BalanceteConformidadeIntegrationTest {

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

        enteA = criarEnte("44444444444401", "Ente Balancete A");
    }

    // ------------------------------------------------------------------
    // 1. Saldo anterior/movimento D/movimento C/saldo atual batem com os
    //    fatos inseridos; contas não-escrituráveis e sem movimento ficam
    //    de fora (razao-contabil-schema.md §Saldos).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("valores do balancete batem com os fatos do razão; filtra conta escriturável sem movimento")
    void valoresDoBalanceteBatemComOsFatosDoRazao() throws SQLException {
        String caixa = criarConta(enteA, "BAL1-1.1.1", "Caixa", "patrimonial", "D", true);
        String fornecedores = criarConta(enteA, "BAL1-2.1.3", "Fornecedores a Pagar", "patrimonial", "C", true);
        String despesa = criarConta(enteA, "BAL1-3.3.90", "Despesas Correntes (VPD)", "patrimonial", "D", true);
        criarConta(enteA, "BAL1-1.9.9", "Conta nunca usada", "patrimonial", "D", true);

        String periodoAnterior = criarPeriodo(enteA, 2026, 1);
        registrarFato(enteA, periodoAnterior, "abertura", "saldo inicial", null,
                debito(caixa, "500.00"), credito(fornecedores, "500.00"));

        String periodoAlvo = criarPeriodo(enteA, 2026, 2);
        registrarFato(enteA, periodoAlvo, "liquidacao", "compra a prazo", null,
                debito(despesa, "300.00"), credito(fornecedores, "300.00"));
        registrarFato(enteA, periodoAlvo, "pagamento", "pagamento parcial a fornecedor", null,
                debito(fornecedores, "200.00"), credito(caixa, "200.00"));

        Balancete balancete = consultarBalancete(enteA, enteA, 2026, 2);

        assertThat(balancete.linhas())
                .as("conta escriturável nunca movimentada (BAL1-1.9.9) não aparece")
                .extracting(LinhaBalancete::codigo)
                .containsExactlyInAnyOrder("BAL1-1.1.1", "BAL1-2.1.3", "BAL1-3.3.90");

        LinhaBalancete linhaCaixa = linha(balancete, "BAL1-1.1.1");
        assertThat(linhaCaixa.saldoAnterior()).isEqualTo(Dinheiro.de("500.00"));
        assertThat(linhaCaixa.movimentoDebito()).isEqualTo(Dinheiro.zero());
        assertThat(linhaCaixa.movimentoCredito()).isEqualTo(Dinheiro.de("200.00"));
        assertThat(linhaCaixa.saldoAtual()).isEqualTo(Dinheiro.de("300.00"));

        LinhaBalancete linhaFornecedores = linha(balancete, "BAL1-2.1.3");
        assertThat(linhaFornecedores.saldoAnterior()).isEqualTo(Dinheiro.de("-500.00"));
        assertThat(linhaFornecedores.movimentoDebito()).isEqualTo(Dinheiro.de("200.00"));
        assertThat(linhaFornecedores.movimentoCredito()).isEqualTo(Dinheiro.de("300.00"));
        assertThat(linhaFornecedores.saldoAtual()).isEqualTo(Dinheiro.de("-600.00"));

        LinhaBalancete linhaDespesa = linha(balancete, "BAL1-3.3.90");
        assertThat(linhaDespesa.saldoAnterior()).isEqualTo(Dinheiro.zero());
        assertThat(linhaDespesa.movimentoDebito()).isEqualTo(Dinheiro.de("300.00"));
        assertThat(linhaDespesa.movimentoCredito()).isEqualTo(Dinheiro.zero());
        assertThat(linhaDespesa.saldoAtual()).isEqualTo(Dinheiro.de("300.00"));

        assertThat(balancete.totalMovimentoDebito()).isEqualTo(Dinheiro.de("500.00"));
        assertThat(balancete.totalMovimentoCredito()).isEqualTo(Dinheiro.de("500.00"));
        assertThat(balancete.confere())
                .as("cada fato já é Σdébito=Σcrédito (trava 1); a soma de qualquer período também deve bater")
                .isTrue();
    }

    // ------------------------------------------------------------------
    // 1b. Filtro de conta não-escriturável — cenário sintético via SQL direto
    //     (RAZ-4 valida "só escriturável recebe lançamento" na APLICAÇÃO, não
    //     no schema; aqui provamos que, mesmo se essa trava de aplicação
    //     falhasse, a conta sintética nunca aparece no relatório). Não se
    //     afirma confere()==true aqui: ao postar num dos lados de um fato
    //     numa conta filtrada, o relatório VISÍVEL perde a contrapartida
    //     desse lado — a Σdébito=Σcrédito do banco (trava 1) continua intacta
    //     no razão, só não aparece inteira neste read-model filtrado.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("conta não-escriturável nunca aparece no balancete, mesmo recebendo lançamento direto via SQL")
    void contaNaoEscrituravelNuncaApareceNoBalanceteMesmoComLancamentoDireto() throws SQLException {
        String sintetica = criarConta(enteA, "BAL1B-1.0", "Ativo (sintética)", "patrimonial", "D", false);
        String contrapartida = criarConta(enteA, "BAL1B-9.9.9", "Contrapartida", "patrimonial", "D", true);
        String periodo = criarPeriodo(enteA, 2026, 6);

        registrarFato(enteA, periodo, "empenho", "movimento espurio em conta sintetica (RAZ-4 e regra de aplicacao)", null,
                debito(sintetica, "50.00"), credito(contrapartida, "50.00"));

        Balancete balancete = consultarBalancete(enteA, enteA, 2026, 6);

        assertThat(balancete.linhas())
                .as("a conta sintética nunca aparece, mesmo tendo recebido lançamento")
                .extracting(LinhaBalancete::codigo)
                .containsExactly("BAL1B-9.9.9");
    }

    // ------------------------------------------------------------------
    // 2. Append-only: um estorno soma ao movimento (débito E crédito), não
    //    apaga nem substitui o lançamento original — o balancete precisa
    //    refletir os DOIS lançamentos, não um estado "líquido".
    // ------------------------------------------------------------------

    @Test
    @DisplayName("estorno soma ao movimento em ambos os lados (append-only) em vez de apagar o lançamento original")
    void estornoApareceComoMovimentoAdicionalNaoComoApagamento() throws SQLException {
        String despesa = criarConta(enteA, "BAL2-3.3.90", "Despesas Correntes (VPD)", "patrimonial", "D", true);
        String fornecedores = criarConta(enteA, "BAL2-2.1.3", "Fornecedores a Pagar", "patrimonial", "C", true);
        String periodo = criarPeriodo(enteA, 2026, 3);

        String fatoErrado = registrarFato(enteA, periodo, "liquidacao", "liquidacao lancada por engano", null,
                debito(despesa, "100.00"), credito(fornecedores, "100.00"));
        estornarFato(enteA, periodo, fatoErrado, "estorno - liquidacao lancada por engano");

        Balancete balancete = consultarBalancete(enteA, enteA, 2026, 3);

        LinhaBalancete linhaDespesa = linha(balancete, "BAL2-3.3.90");
        assertThat(linhaDespesa.movimentoDebito())
                .as("lançamento original (débito) continua contado — não foi apagado pelo estorno")
                .isEqualTo(Dinheiro.de("100.00"));
        assertThat(linhaDespesa.movimentoCredito())
                .as("estorno soma um NOVO lançamento (crédito), não subtrai do débito original")
                .isEqualTo(Dinheiro.de("100.00"));
        assertThat(linhaDespesa.saldoAtual())
                .as("efeito líquido é zero, mas via dois lançamentos, não zero")
                .isEqualTo(Dinheiro.zero());

        LinhaBalancete linhaFornecedores = linha(balancete, "BAL2-2.1.3");
        assertThat(linhaFornecedores.movimentoDebito()).isEqualTo(Dinheiro.de("100.00"));
        assertThat(linhaFornecedores.movimentoCredito()).isEqualTo(Dinheiro.de("100.00"));
        assertThat(linhaFornecedores.saldoAtual()).isEqualTo(Dinheiro.zero());

        assertThat(balancete.confere()).isTrue();
    }

    // ------------------------------------------------------------------
    // 3. RLS: o balancete de um ente nunca inclui contas/fatos de outro,
    //    mesmo que o parâmetro de consulta divirja da sessão (defesa em
    //    profundidade além do filtro `c.ente_id = ?` da própria SQL).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("balancete nunca vaza dados de outro ente, mesmo com enteId de consulta divergente da sessão RLS")
    void balanceteRespeitaIsolamentoMultiTenantMesmoComEnteIdDivergenteDaSessao() throws SQLException {
        // Entes dedicados (não enteA/enteB): "saldo anterior" acumula TUDO antes do período
        // pedido, então reusar um ente com histórico de outros testes contaminaria a contagem
        // de linhas deste teste — o que se quer isolar aqui é o ISOLAMENTO ENTRE ENTES, não
        // entre períodos do mesmo ente.
        String enteC = criarEnte("44444444444403", "Ente Balancete C (RLS)");
        String enteD = criarEnte("44444444444404", "Ente Balancete D (RLS)");

        String caixaC = criarConta(enteC, "BAL3-1.1.1", "Caixa", "patrimonial", "D", true);
        String fornecedoresC = criarConta(enteC, "BAL3-2.1.3", "Fornecedores a Pagar", "patrimonial", "C", true);
        String periodoC = criarPeriodo(enteC, 2026, 1);
        registrarFato(enteC, periodoC, "empenho", "movimento do ente C", null,
                debito(caixaC, "900.00"), credito(fornecedoresC, "900.00"));

        // mesmos códigos do ente C de propósito: prova que o isolamento é por ente_id/RLS, não por código único.
        String caixaD = criarConta(enteD, "BAL3-1.1.1", "Caixa", "patrimonial", "D", true);
        String fornecedoresD = criarConta(enteD, "BAL3-2.1.3", "Fornecedores a Pagar", "patrimonial", "C", true);
        String periodoD = criarPeriodo(enteD, 2026, 1);
        registrarFato(enteD, periodoD, "empenho", "movimento do ente D", null,
                debito(caixaD, "777.00"), credito(fornecedoresD, "777.00"));

        Balancete balanceteC = consultarBalancete(enteC, enteC, 2026, 1);
        assertThat(balanceteC.linhas()).hasSize(2);
        LinhaBalancete linhaCaixaC = linha(balanceteC, "BAL3-1.1.1");
        assertThat(linhaCaixaC.contaId()).isEqualTo(UUID.fromString(caixaC));
        assertThat(linhaCaixaC.saldoAtual()).isEqualTo(Dinheiro.de("900.00"));

        Balancete balanceteDVistoDeD = consultarBalancete(enteD, enteD, 2026, 1);
        assertThat(linha(balanceteDVistoDeD, "BAL3-1.1.1").saldoAtual())
                .as("ente D tem dados reais e recuperáveis na própria sessão")
                .isEqualTo(Dinheiro.de("777.00"));

        Balancete balanceteDVistoDeSessaoC = consultarBalancete(enteC, enteD, 2026, 1);
        assertThat(balanceteDVistoDeSessaoC.linhas())
                .as("RLS bloqueia mesmo quando o parâmetro de consulta pede o ente D mas a sessão está setada para o ente C")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // 4. Exercício/mês sem período cadastrado resolve para balancete de
    //    linhas vazias — não vaza dados de OUTRO período existente.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("exercício/mês sem período cadastrado devolve balancete vazio, sem vazar dados de outro período")
    void periodoNaoCadastradoDevolveBalanceteVazio() throws SQLException {
        String caixa = criarConta(enteA, "BAL4-1.1.1", "Caixa", "patrimonial", "D", true);
        String fornecedores = criarConta(enteA, "BAL4-2.1.3", "Fornecedores a Pagar", "patrimonial", "C", true);
        String periodoExistente = criarPeriodo(enteA, 2026, 7);
        registrarFato(enteA, periodoExistente, "empenho", "movimento em periodo existente (mes 7)", null,
                debito(caixa, "123.45"), credito(fornecedores, "123.45"));

        Balancete balancete = consultarBalancete(enteA, enteA, 2026, 8);

        assertThat(balancete.linhas()).as("mes 8 nunca foi cadastrado para este ente/exercício").isEmpty();
        assertThat(balancete.confere()).isTrue();
    }

    // ------------------------------------------------------------------
    // Infraestrutura do teste
    // ------------------------------------------------------------------

    private record Partida(String contaId, char natureza, String valor) {
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
            String fatoEstornadoId, Partida... partidas) throws SQLException {
        return comoAppLogin(enteId, st -> {
            long numeroSeq;
            try (ResultSet rs = st.executeQuery("select proximo_numero_seq()")) {
                rs.next();
                numeroSeq = rs.getLong(1);
            }
            String fatoId = UUID.randomUUID().toString();
            st.execute("""
                    insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem, fato_estornado_id)
                    values ('%s', '%s', %d, current_date, '%s', '%s', '%s', 'golden-set-balancete', %s)
                    """.formatted(fatoId, enteId, numeroSeq, periodoId, tipoEvento, historico,
                    fatoEstornadoId == null ? "null" : "'" + fatoEstornadoId + "'"));
            for (Partida p : partidas) {
                st.execute("insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s','%s','%s','%s',%s)"
                        .formatted(enteId, fatoId, p.contaId(), p.natureza(), p.valor()));
            }
            return fatoId;
        });
    }

    /** Espelha {@code FatoContabil.estornar()}: novo fato, lançamentos com natureza invertida, mesma conta/valor. */
    private static String estornarFato(String enteId, String periodoId, String fatoOriginalId, String historico)
            throws SQLException {
        List<Partida> invertidas = comoAppLogin(enteId, st -> {
            List<Partida> lista = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                    "select conta_id, natureza, valor from lancamento where fato_id = '" + fatoOriginalId + "'")) {
                while (rs.next()) {
                    char naturezaInvertida = "D".equals(rs.getString("natureza")) ? 'C' : 'D';
                    lista.add(new Partida(rs.getString("conta_id"), naturezaInvertida, rs.getBigDecimal("valor").toPlainString()));
                }
            }
            return lista;
        });
        return registrarFato(enteId, periodoId, "estorno", historico, fatoOriginalId, invertidas.toArray(new Partida[0]));
    }

    private static Balancete consultarBalancete(String enteSessao, String enteConsulta, int exercicio, int mes)
            throws SQLException {
        try (Connection conexao = appLoginConnection()) {
            conexao.setAutoCommit(false);
            try (Statement st = conexao.createStatement()) {
                st.execute("set local app.ente_id = '" + enteSessao + "'");
            }
            PostgresBalancetePort port = new PostgresBalancetePort(new JdbcTemplate(new SingleConnectionDataSource(conexao, true)));
            Balancete resultado = port.balancete(TenantId.de(enteConsulta), exercicio, mes);
            conexao.commit();
            return resultado;
        }
    }

    private static LinhaBalancete linha(Balancete balancete, String codigo) {
        return balancete.linhas().stream().filter(l -> l.codigo().equals(codigo)).findFirst().orElse(null);
    }
}
