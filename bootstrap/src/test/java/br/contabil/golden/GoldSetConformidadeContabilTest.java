package br.contabil.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Gold set de conformidade contábil (RAZ-19) — cenários curados × ESTADO ESPERADO
 * do razão (lançamentos, Σdébito=Σcrédito, saldos derivados via {@code saldo_conta}),
 * com um Postgres real (Testcontainers) e a migration V1 tal como em produção.
 *
 * <p>Refs.: {@code docs/arquitetura-tecnica/razao-contabil-schema.md} (schema/travas),
 * {@code docs/04-fluxos.md} §2 (execução da despesa) e §8 (fechamento de período),
 * {@code docs/10-modelo-dados.md} (cardinalidades, tipos de empenho, ciclo de vida),
 * {@code docs/arquitetura-tecnica/README.md} §6 (UC1, UC6 — stress test lógico).
 *
 * <p><b>Nota de escopo:</b> {@code execucao-domain}/{@code execucao-application} ainda não
 * têm nenhum agregado (só {@code package-info.java} — empenho/liquidação/pagamento como
 * domínio próprio são, hoje, só intenção de design; ver {@code docs/10-modelo-dados.md}).
 * O motor de razão em si, porém, já é código real:
 * {@code razao-domain.FatoContabil}, {@code razao-application.RegistrarFatoContabil}/
 * {@code EstornarFatoContabil} e os adapters {@code razao-infra.Postgres*} (ver
 * {@code docs/arquitetura-tecnica/motor-razao-partidas-dobradas.md}). Este gold set
 * INTENCIONALMENTE não passa por eles: insere {@code fato_contabil}/{@code lancamento}
 * direto via SQL (autenticado como {@code app_login}, com {@code proximo_numero_seq()} e
 * as travas do banco realmente ativas) para provar o schema/as travas isolados de qualquer
 * bug de aplicação — o mesmo motivo de {@code RazaoContabilTravasTest}, aqui sob cenários de
 * negócio em vez de ataques pontuais a cada trava. Não substitui os testes de unidade
 * mockados de {@code RegistrarFatoContabilTest}/{@code EstornarFatoContabilTest}, e também
 * não prova a integração real desses casos de uso com os adapters Postgres (
 * {@code PostgresFatoContabilRepository}, {@code PostgresPeriodoContabilPort}, etc.) — esse
 * gap era rastreado aqui (RAZ-27) e agora é coberto por
 * {@code RegistrarEstornarFatoContabilIntegrationTest} (bootstrap), que sobe o contexto
 * Spring real e exercita {@code RegistrarFatoContabil}/{@code EstornarFatoContabil} como
 * beans gerenciados contra Postgres real.
 *
 * <p><b>Gaps encontrados (fora do escopo testável hoje, registrar no backlog):</b>
 * <ul>
 *   <li>Não há tabela {@code dotacao}/{@code saldo_disponivel} — "empenho + reforços ≤
 *       crédito da dotação" (10-modelo-dados.md, art. 59) não é uma constraint física;
 *       idem "pago ≤ liquidado ≤ empenhado" (não há chave de amarração empenho→liquidação→
 *       pagamento no razão genérico).</li>
 *   <li>{@code tipo_evento} não distingue reforço/anulação de empenho nem "inscrição em
 *       restos a pagar" — aqui restos a pagar é o saldo remanescente de
 *       "Fornecedores a Pagar" que sobrevive ao fechamento do período, não um fato próprio
 *       (cenário 7).</li>
 *   <li>Não há tabela {@code doc_suporte} nem beneficiário obrigatório — as travas de UC1
 *       sobre documento de suporte/beneficiário do README §6 não são cobertas aqui.</li>
 * </ul>
 *
 * <p>Plano de contas: ilustrativo, não é PCASP oficial validado na fonte (mesma ressalva
 * já registrada em razao-contabil-schema.md).
 */
@Testcontainers(disabledWithoutDocker = true)
class GoldSetConformidadeContabilTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static String enteA;
    private static String periodoAberto;

    @BeforeAll
    static void preparaBase() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection dono = adminConnection();
                Statement st = dono.createStatement()) {
            st.execute("create role app_login login password 'app_login'");
            st.execute("grant app_role to app_login");
            try (ResultSet rs = st.executeQuery(
                    "insert into ente (cnpj, nome, esfera) values ('33333333333333', 'Ente Gold Set', 'municipio') returning id")) {
                rs.next();
                enteA = rs.getString(1);
            }
        }

        periodoAberto = criarPeriodo(enteA, 2026, 1);
    }

    // ------------------------------------------------------------------
    // 1. Empenho ordinário (art. 60) — valor certo, pago de uma vez:
    //    1 empenho : 1 liquidação : 1 pagamento (10-modelo-dados.md "Tipos de empenho")
    // ------------------------------------------------------------------

    @Test
    void empenhoOrdinarioComLiquidacaoEPagamentoIntegralNumaUnicaParcela() throws SQLException {
        String caixa = criarConta("GS1-1.1.1", "Caixa e Equivalentes", "patrimonial", "D");
        String fornecedores = criarConta("GS1-2.1.3", "Fornecedores a Pagar", "patrimonial", "C");
        String despesa = criarConta("GS1-3.3.90", "Despesas Correntes (VPD)", "patrimonial", "D");
        String dotacao = criarConta("GS1-5.2.2", "Credito Disponivel (Dotacao)", "orcamentaria", "C");
        String controleEmpenho = criarConta("GS1-6.2.2", "Despesas Empenhadas a Liquidar", "controle", "D");

        registrarFato(periodoAberto, "empenho", "empenho ordinario - contrato unico",
                null, debito(controleEmpenho, "1000.00"), credito(dotacao, "1000.00"));

        registrarFato(periodoAberto, "liquidacao", "liquidacao integral do empenho ordinario",
                null, debito(despesa, "1000.00"), credito(fornecedores, "1000.00"));

        String fatoPagamento = registrarFato(periodoAberto, "pagamento", "pagamento integral da liquidacao",
                null, debito(fornecedores, "1000.00"), credito(caixa, "1000.00"));

        assertPartidasBalanceadas(fatoPagamento);
        assertThat(saldo(controleEmpenho)).as("empenho registrado em conta de controle").isEqualByComparingTo("1000.00");
        assertThat(saldo(dotacao)).as("dotacao consumida pelo empenho").isEqualByComparingTo("-1000.00");
        assertThat(saldo(despesa)).as("despesa reconhecida na liquidacao").isEqualByComparingTo("1000.00");
        assertThat(saldo(fornecedores)).as("liquidado e integralmente pago no mesmo passo: saldo zerado").isEqualByComparingTo("0.00");
        assertThat(saldo(caixa)).as("caixa reduzido pelo pagamento integral").isEqualByComparingTo("-1000.00");
    }

    // ------------------------------------------------------------------
    // 2. Empenho estimativo (art. 60 §2º) — montante não determinável
    //    (água, luz, telefone): 1 empenho : N liquidações
    // ------------------------------------------------------------------

    @Test
    void empenhoEstimativoComMultiplasLiquidacoesDeValorNaoDeterminavelAntecipadamente() throws SQLException {
        String fornecedores = criarConta("GS2-2.1.3", "Fornecedores a Pagar", "patrimonial", "C");
        String despesa = criarConta("GS2-3.3.90", "Despesas Correntes (VPD)", "patrimonial", "D");
        String dotacao = criarConta("GS2-5.2.2", "Credito Disponivel (Dotacao)", "orcamentaria", "C");
        String controleEmpenho = criarConta("GS2-6.2.2", "Despesas Empenhadas a Liquidar", "controle", "D");

        registrarFato(periodoAberto, "empenho", "empenho estimativo - energia eletrica (valor previsto)",
                null, debito(controleEmpenho, "600.00"), credito(dotacao, "600.00"));

        // 3 liquidações mensais de valor real (varia mês a mês; a soma pode superar a
        // estimativa, porque é PREVISÃO, não teto — ao contrário da dotação, não há
        // constraint física de "liquidação <= empenho" no schema atual (gap, ver Javadoc).
        registrarFato(periodoAberto, "liquidacao", "liquidacao energia - competencia 01",
                null, debito(despesa, "250.00"), credito(fornecedores, "250.00"));
        registrarFato(periodoAberto, "liquidacao", "liquidacao energia - competencia 02",
                null, debito(despesa, "230.00"), credito(fornecedores, "230.00"));
        String ultimaLiquidacao = registrarFato(periodoAberto, "liquidacao", "liquidacao energia - competencia 03",
                null, debito(despesa, "260.00"), credito(fornecedores, "260.00"));

        assertPartidasBalanceadas(ultimaLiquidacao);
        assertThat(saldo(despesa)).as("soma das 3 liquidacoes reais").isEqualByComparingTo("740.00");
        // saldo_conta = ΣD - ΣC (razao-contabil-schema.md); fornecedores so recebeu
        // credito ate aqui, entao o saldo bruto da view e negativo (natureza_saldo='C').
        assertThat(saldo(fornecedores)).as("nenhuma liquidacao paga ainda").isEqualByComparingTo("-740.00");
        assertThat(saldo(controleEmpenho)).as("valor estimado do empenho, registrado uma unica vez").isEqualByComparingTo("600.00");
        assertThat(saldo(dotacao)).isEqualByComparingTo("-600.00");
    }

    // ------------------------------------------------------------------
    // 3. Empenho global (art. 60 §3º) — despesa contratual parcelada:
    //    1 empenho : N liquidações : N pagamentos (parcelamento)
    // ------------------------------------------------------------------

    @Test
    void empenhoGlobalComLiquidacaoEPagamentoParceladosPorParcela() throws SQLException {
        String caixa = criarConta("GS3-1.1.1", "Caixa e Equivalentes", "patrimonial", "D");
        String fornecedores = criarConta("GS3-2.1.3", "Fornecedores a Pagar", "patrimonial", "C");
        String despesa = criarConta("GS3-3.3.90", "Despesas Correntes (VPD)", "patrimonial", "D");
        String dotacao = criarConta("GS3-5.2.2", "Credito Disponivel (Dotacao)", "orcamentaria", "C");
        String controleEmpenho = criarConta("GS3-6.2.2", "Despesas Empenhadas a Liquidar", "controle", "D");

        registrarFato(periodoAberto, "empenho", "empenho global - contrato parcelado em 3x",
                null, debito(controleEmpenho, "3000.00"), credito(dotacao, "3000.00"));

        String ultimoPagamento = null;
        for (int parcela = 1; parcela <= 3; parcela++) {
            registrarFato(periodoAberto, "liquidacao", "liquidacao da parcela " + parcela + "/3",
                    null, debito(despesa, "1000.00"), credito(fornecedores, "1000.00"));
            ultimoPagamento = registrarFato(periodoAberto, "pagamento", "pagamento da parcela " + parcela + "/3",
                    null, debito(fornecedores, "1000.00"), credito(caixa, "1000.00"));
        }

        assertPartidasBalanceadas(ultimoPagamento);
        assertThat(saldo(despesa)).as("3 parcelas liquidadas").isEqualByComparingTo("3000.00");
        assertThat(saldo(fornecedores)).as("cada parcela paga logo apos liquidada").isEqualByComparingTo("0.00");
        assertThat(saldo(caixa)).isEqualByComparingTo("-3000.00");
        assertThat(saldo(controleEmpenho)).isEqualByComparingTo("3000.00");
        assertThat(saldo(dotacao)).isEqualByComparingTo("-3000.00");
    }

    // ------------------------------------------------------------------
    // 4. Liquidação parcial (arts. 62-65 admitem pagamento parcial; aqui a
    //    própria liquidação chega em duas etapas sobre o mesmo empenho)
    // ------------------------------------------------------------------

    @Test
    void liquidacaoParcialEmDuasEtapasSobreOMesmoEmpenho() throws SQLException {
        String fornecedores = criarConta("GS4-2.1.3", "Fornecedores a Pagar", "patrimonial", "C");
        String despesa = criarConta("GS4-3.3.90", "Despesas Correntes (VPD)", "patrimonial", "D");
        String dotacao = criarConta("GS4-5.2.2", "Credito Disponivel (Dotacao)", "orcamentaria", "C");
        String controleEmpenho = criarConta("GS4-6.2.2", "Despesas Empenhadas a Liquidar", "controle", "D");

        registrarFato(periodoAberto, "empenho", "empenho para liquidacao parcial",
                null, debito(controleEmpenho, "1000.00"), credito(dotacao, "1000.00"));

        registrarFato(periodoAberto, "liquidacao", "liquidacao parcial 1/2 - entrega inicial",
                null, debito(despesa, "600.00"), credito(fornecedores, "600.00"));

        assertThat(saldo(despesa)).as("estado intermediario apos a 1a liquidacao parcial").isEqualByComparingTo("600.00");
        // saldo_conta = ΣD - ΣC; fornecedores (natureza_saldo='C') so recebeu credito ate aqui.
        assertThat(saldo(fornecedores)).isEqualByComparingTo("-600.00");

        registrarFato(periodoAberto, "liquidacao", "liquidacao parcial 2/2 - entrega final",
                null, debito(despesa, "400.00"), credito(fornecedores, "400.00"));

        assertThat(saldo(despesa)).as("soma das duas liquidacoes parciais = valor empenhado").isEqualByComparingTo("1000.00");
        assertThat(saldo(fornecedores)).isEqualByComparingTo("-1000.00");
    }

    // ------------------------------------------------------------------
    // 5. Pagamento parcial (arts. 62-65: pagamento pode ser parcial)
    // ------------------------------------------------------------------

    @Test
    void pagamentoParcialEmDuasEtapasSobreAMesmaLiquidacao() throws SQLException {
        String caixa = criarConta("GS5-1.1.1", "Caixa e Equivalentes", "patrimonial", "D");
        String fornecedores = criarConta("GS5-2.1.3", "Fornecedores a Pagar", "patrimonial", "C");
        String despesa = criarConta("GS5-3.3.90", "Despesas Correntes (VPD)", "patrimonial", "D");
        String dotacao = criarConta("GS5-5.2.2", "Credito Disponivel (Dotacao)", "orcamentaria", "C");
        String controleEmpenho = criarConta("GS5-6.2.2", "Despesas Empenhadas a Liquidar", "controle", "D");

        registrarFato(periodoAberto, "empenho", "empenho para pagamento parcial",
                null, debito(controleEmpenho, "1000.00"), credito(dotacao, "1000.00"));
        registrarFato(periodoAberto, "liquidacao", "liquidacao integral",
                null, debito(despesa, "1000.00"), credito(fornecedores, "1000.00"));

        registrarFato(periodoAberto, "pagamento", "pagamento parcial 1/2",
                null, debito(fornecedores, "700.00"), credito(caixa, "700.00"));

        // saldo_conta = ΣD - ΣC: credito 1000 (liquidacao) - debito 700 (pagamento) = -300.
        assertThat(saldo(fornecedores)).as("resta a pagar apos o 1o pagamento parcial").isEqualByComparingTo("-300.00");
        assertThat(saldo(caixa)).isEqualByComparingTo("-700.00");

        registrarFato(periodoAberto, "pagamento", "pagamento parcial 2/2 - quitacao",
                null, debito(fornecedores, "300.00"), credito(caixa, "300.00"));

        assertThat(saldo(fornecedores)).as("liquidacao integralmente paga").isEqualByComparingTo("0.00");
        assertThat(saldo(caixa)).isEqualByComparingTo("-1000.00");
    }

    // ------------------------------------------------------------------
    // 6. Estorno (fluxo 4) — novo fato com lançamentos invertidos; o
    //    original permanece íntegro (nunca é alterado nem apagado)
    // ------------------------------------------------------------------

    @Test
    void estornoRegistraNovoFatoComLancamentosInvertidosSemAlterarOOriginal() throws SQLException {
        String dotacao = criarConta("GS6-5.2.2", "Credito Disponivel (Dotacao)", "orcamentaria", "C");
        String controleEmpenho = criarConta("GS6-6.2.2", "Despesas Empenhadas a Liquidar", "controle", "D");

        String fatoEmpenho = registrarFato(periodoAberto, "empenho", "empenho lancado por engano",
                null, debito(controleEmpenho, "1000.00"), credito(dotacao, "1000.00"));

        assertThat(saldo(controleEmpenho)).isEqualByComparingTo("1000.00");
        assertThat(saldo(dotacao)).isEqualByComparingTo("-1000.00");

        String fatoEstorno = estornarFato(periodoAberto, fatoEmpenho, "estorno - empenho lancado por engano");

        assertPartidasBalanceadas(fatoEstorno);
        assertThat(saldo(controleEmpenho)).as("estorno neutraliza o saldo, nao apaga o lancamento original").isEqualByComparingTo("0.00");
        assertThat(saldo(dotacao)).isEqualByComparingTo("0.00");

        FatoInfo estorno = lerFato(fatoEstorno);
        assertThat(estorno.tipoEvento()).isEqualTo("estorno");
        assertThat(estorno.fatoEstornadoId()).isEqualTo(fatoEmpenho);

        assertThat(valorLancamento(fatoEmpenho, controleEmpenho, 'D'))
                .as("fato original permanece intacto apos o estorno")
                .isEqualByComparingTo("1000.00");
        assertThat(valorLancamento(fatoEmpenho, dotacao, 'C')).isEqualByComparingTo("1000.00");
    }

    // ------------------------------------------------------------------
    // 7. Restos a pagar — liquidado, não pago; o período fecha e o saldo
    //    de "Fornecedores a Pagar" atravessa o fechamento intacto, sendo
    //    quitado só no período seguinte (não há tipo_evento próprio para
    //    "inscrição em restos a pagar" hoje — ver Javadoc/gap)
    // ------------------------------------------------------------------

    @Test
    void restosAPagarSaldoNaoPagoAtravessaFechamentoDePeriodoEQuitadoNoPeriodoSeguinte() throws SQLException {
        String caixa = criarConta("GS7-1.1.1", "Caixa e Equivalentes", "patrimonial", "D");
        String fornecedores = criarConta("GS7-2.1.3", "Fornecedores a Pagar", "patrimonial", "C");
        String despesa = criarConta("GS7-3.3.90", "Despesas Correntes (VPD)", "patrimonial", "D");
        String dotacao = criarConta("GS7-5.2.2", "Credito Disponivel (Dotacao)", "orcamentaria", "C");
        String controleEmpenho = criarConta("GS7-6.2.2", "Despesas Empenhadas a Liquidar", "controle", "D");

        String periodoN = criarPeriodo(enteA, 2026, 3);
        registrarFato(periodoN, "empenho", "empenho - exercicio a encerrar",
                null, debito(controleEmpenho, "1000.00"), credito(dotacao, "1000.00"));
        registrarFato(periodoN, "liquidacao", "liquidacao integral - sem pagamento ate o fechamento",
                null, debito(despesa, "1000.00"), credito(fornecedores, "1000.00"));

        fecharPeriodo(periodoN);

        assertThatThrownBy(() -> registrarFato(periodoN, "pagamento", "tentativa de pagar no periodo ja encerrado",
                null, debito(fornecedores, "1000.00"), credito(caixa, "1000.00")))
                .as("periodo encerrado bloqueia novo fato (trava 3), mesmo para quitar um resto a pagar")
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Periodo encerrado");

        // saldo_conta = ΣD - ΣC; fornecedores (natureza_saldo='C') so recebeu credito ate aqui.
        assertThat(saldo(fornecedores))
                .as("saldo nao pago sobrevive ao fechamento do periodo - e o resto a pagar processado")
                .isEqualByComparingTo("-1000.00");

        String periodoSeguinte = criarPeriodo(enteA, 2026, 4);
        registrarFato(periodoSeguinte, "pagamento", "quitacao do resto a pagar processado",
                null, debito(fornecedores, "1000.00"), credito(caixa, "1000.00"));

        assertThat(saldo(fornecedores)).as("resto a pagar quitado no periodo seguinte").isEqualByComparingTo("0.00");
        assertThat(saldo(caixa)).isEqualByComparingTo("-1000.00");
    }

    // ------------------------------------------------------------------
    // 8. Fechamento de período — bloqueia novo fato no período fechado;
    //    correção de erro só por estorno + novo lançamento no período
    //    aberto seguinte (fluxo 8: "ajuste só por estorno/retificação")
    // ------------------------------------------------------------------

    @Test
    void fechamentoDePeriodoBloqueiaNovoFatoECorrecaoDeErroSoAconteceViaEstornoNoPeriodoSeguinte() throws SQLException {
        String dotacao = criarConta("GS8-5.2.2", "Credito Disponivel (Dotacao)", "orcamentaria", "C");
        String controleEmpenho = criarConta("GS8-6.2.2", "Despesas Empenhadas a Liquidar", "controle", "D");

        String periodoN = criarPeriodo(enteA, 2026, 5);
        String fatoErrado = registrarFato(periodoN, "empenho", "empenho lancado com valor errado (500.00, deveria ser 400.00)",
                null, debito(controleEmpenho, "500.00"), credito(dotacao, "500.00"));

        fecharPeriodo(periodoN);

        assertThatThrownBy(() -> registrarFato(periodoN, "empenho", "tentativa de corrigir direto no periodo encerrado",
                null, debito(controleEmpenho, "1.00"), credito(dotacao, "1.00")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Periodo encerrado");

        String periodoSeguinte = criarPeriodo(enteA, 2026, 6);
        String fatoEstorno = estornarFato(periodoSeguinte, fatoErrado, "correcao: valor lancado a maior, empenho deveria ser 400.00");
        registrarFato(periodoSeguinte, "empenho", "empenho correto (retificacao)",
                null, debito(controleEmpenho, "400.00"), credito(dotacao, "400.00"));

        assertThat(valorLancamento(fatoErrado, controleEmpenho, 'D'))
                .as("fato original (errado) permanece intacto - nunca e alterado nem apagado")
                .isEqualByComparingTo("500.00");
        FatoInfo estorno = lerFato(fatoEstorno);
        assertThat(estorno.periodoId())
                .as("correcao e registrada no periodo aberto seguinte, nao no periodo fechado onde o erro ocorreu")
                .isEqualTo(periodoSeguinte);

        assertThat(saldo(controleEmpenho))
                .as("liquido: 500 (errado) - 500 (estorno) + 400 (correto) = 400, como se o valor certo tivesse sido lancado desde o inicio")
                .isEqualByComparingTo("400.00");
        assertThat(saldo(dotacao)).isEqualByComparingTo("-400.00");
    }

    // ------------------------------------------------------------------
    // Infraestrutura do gold set
    // ------------------------------------------------------------------

    private record Partida(String contaId, char natureza, String valor) {
    }

    private static Partida debito(String contaId, String valor) {
        return new Partida(contaId, 'D', valor);
    }

    private static Partida credito(String contaId, String valor) {
        return new Partida(contaId, 'C', valor);
    }

    private record FatoInfo(String tipoEvento, String fatoEstornadoId, String periodoId) {
    }

    @FunctionalInterface
    private interface OperacaoAppLogin<T> {
        T executar(Statement st) throws SQLException;
    }

    /**
     * Abre uma conexão como {@code app_login} (o papel que a aplicação usaria em
     * produção — nunca o dono/superuser), seta {@code app.ente_id} da sessão (a
     * variável que a RLS e {@code proximo_numero_seq()} leem) e comita ao final.
     * Usar {@code app_login} em vez do admin em todo cenário de negócio (a exceção é o
     * bootstrap em {@code @BeforeAll}: criar o ente e o próprio role {@code app_login}
     * exige a conexão admin) também verifica, de passagem, que os GRANTs da migration
     * bastam para o fluxo completo.
     */
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

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String criarConta(String codigo, String descricao, String naturezaInformacao, String naturezaSaldo)
            throws SQLException {
        return comoAppLogin(enteA, st -> {
            try (ResultSet rs = st.executeQuery(
                    "insert into conta_pcasp (ente_id, codigo, descricao, natureza_informacao, natureza_saldo) values ('%s','%s','%s','%s','%s') returning id"
                            .formatted(enteA, codigo, descricao, naturezaInformacao, naturezaSaldo))) {
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

    private static void fecharPeriodo(String periodoId) throws SQLException {
        comoAppLogin(enteA, st -> {
            st.execute("update periodo_contabil set status = 'encerrado', encerrado_em = clock_timestamp() where id = '"
                    + periodoId + "'");
            return null;
        });
    }

    /** Registra um fato via {@code proximo_numero_seq()} na mesma transação — mesmo caminho que um caso de uso real seguiria. */
    private static String registrarFato(String periodoId, String tipoEvento, String historico, String fatoEstornadoId,
            Partida... partidas) throws SQLException {
        return comoAppLogin(enteA, st -> {
            long numeroSeq;
            try (ResultSet rs = st.executeQuery("select proximo_numero_seq()")) {
                rs.next();
                numeroSeq = rs.getLong(1);
            }
            String fatoId = UUID.randomUUID().toString();
            st.execute("""
                    insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem, fato_estornado_id)
                    values ('%s', '%s', %d, current_date, '%s', '%s', '%s', 'golden-set', %s)
                    """.formatted(fatoId, enteA, numeroSeq, periodoId, tipoEvento, historico,
                    fatoEstornadoId == null ? "null" : "'" + fatoEstornadoId + "'"));
            for (Partida p : partidas) {
                st.execute("insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values ('%s','%s','%s','%s',%s)"
                        .formatted(enteA, fatoId, p.contaId(), p.natureza(), p.valor()));
            }
            return fatoId;
        });
    }

    /** Espelha {@code FatoContabil.estornar()} do domínio: novo fato, lançamentos com natureza invertida, mesma conta/valor. */
    private static String estornarFato(String periodoId, String fatoOriginalId, String historico) throws SQLException {
        List<Partida> invertidas = comoAppLogin(enteA, st -> {
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
        return registrarFato(periodoId, "estorno", historico, fatoOriginalId, invertidas.toArray(new Partida[0]));
    }

    private static BigDecimal saldo(String contaId) throws SQLException {
        return comoAppLogin(enteA, st -> {
            try (ResultSet rs = st.executeQuery(
                    "select saldo_devedor_liquido from saldo_conta where conta_id = '" + contaId + "'")) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        });
    }

    private static BigDecimal valorLancamento(String fatoId, String contaId, char natureza) throws SQLException {
        return comoAppLogin(enteA, st -> {
            try (ResultSet rs = st.executeQuery(
                    "select valor from lancamento where fato_id = '" + fatoId + "' and conta_id = '" + contaId
                            + "' and natureza = '" + natureza + "'")) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        });
    }

    private static FatoInfo lerFato(String fatoId) throws SQLException {
        return comoAppLogin(enteA, st -> {
            try (ResultSet rs = st.executeQuery(
                    "select tipo_evento, fato_estornado_id, periodo_id from fato_contabil where id = '" + fatoId + "'")) {
                rs.next();
                return new FatoInfo(rs.getString("tipo_evento"), rs.getString("fato_estornado_id"), rs.getString("periodo_id"));
            }
        });
    }

    /** Prova explícita de Σdébito=Σcrédito (trava 1) — reforça a spec, além do commit já não lançar exceção. */
    private static void assertPartidasBalanceadas(String fatoId) throws SQLException {
        BigDecimal[] somas = comoAppLogin(enteA, st -> {
            try (ResultSet rs = st.executeQuery(
                    "select coalesce(sum(valor) filter (where natureza = 'D'), 0) as deb, "
                            + "coalesce(sum(valor) filter (where natureza = 'C'), 0) as cred "
                            + "from lancamento where fato_id = '" + fatoId + "'")) {
                rs.next();
                return new BigDecimal[] {rs.getBigDecimal("deb"), rs.getBigDecimal("cred")};
            }
        });
        assertThat(somas[0]).as("Sigma debito = Sigma credito no fato %s (trava 1)", fatoId).isEqualByComparingTo(somas[1]);
    }
}
