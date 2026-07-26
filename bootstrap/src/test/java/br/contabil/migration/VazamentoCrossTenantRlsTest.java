package br.contabil.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guardrail de CI bloqueante (ADR-0003): prova, com um Postgres real e a migration V1 tal
 * como em produção, que RLS deny-by-default não vaza UMA LINHA SEQUER entre entes em
 * NENHUMA das 4 tabelas às quais o {@code app_role} tem GRANT select — abre transações com
 * {@code app.ente_id} distinto por ente e confirma zero linhas cruzadas, além do
 * deny-by-default sem a variável setada. Ver razao-contabil-schema.md §"Como o guardião testa
 * isto", trava 4.
 *
 * <p>{@code contador_fato} também tem {@code ente_id} e RLS forçada, mas fica de fora: o
 * {@code app_role} não recebe NENHUM grant nela (só acesso indireto via a função {@code
 * security definer} {@code proximo_numero_seq}), então uma consulta direta como app_login
 * falha com "permission denied" antes mesmo de a RLS entrar em jogo — não há superfície de
 * SELECT cross-tenant a provar ali; o limite é o grant, não a policy.
 *
 * <p>Complementa, sem substituir: {@code RazaoContabilTravasTest} cobre a trava 4 base só em
 * {@code fato_contabil}; {@code RazaoRlsCrossTenantForeignKeyTest} (RAZ-13) cobre a trava 4b
 * (bypass via FK simples no INSERT/DONO). Este teste cobre o lado SELECT — a superfície que
 * um vazamento cross-tenant reprovaria no controle externo (ADR-0003).
 *
 * <p>{@link #saldoContaViewNaoVazaLinhaDeOutroEnte()} guardrail contra o vazamento cross-tenant
 * que uma view com grant select ao app_role teria SEM {@code security_invoker = true} — sem essa
 * cláusula o Postgres avaliaria RLS pelo DONO da view (o superusuário da migration, que ignora
 * RLS), não por quem consulta. A V1 atual já declara {@code security_invoker = true} em {@code
 * saldo_conta}, então este teste passa hoje; ele fica para pegar regressão se a cláusula for
 * removida.
 */
@Testcontainers(disabledWithoutDocker = true)
class VazamentoCrossTenantRlsTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String ENTE_A = "33333333-3333-3333-3333-333333333333";
    private static final String ENTE_B = "44444444-4444-4444-4444-444444444444";
    private static final String PERIODO_A = "aaaaaaaa-3333-3333-3333-aaaaaaaaaaaa";
    private static final String PERIODO_B = "aaaaaaaa-4444-4444-4444-aaaaaaaaaaaa";
    private static final String CONTA_A = "cccccccc-3333-3333-3333-cccccccccccc";
    private static final String CONTA_B = "cccccccc-4444-4444-4444-cccccccccccc";
    private static final String FATO_A = "ffffffff-3333-3333-3333-ffffffffffff";
    private static final String FATO_B = "ffffffff-4444-4444-4444-ffffffffffff";
    private static final String DOTACAO_A = "dddddddd-3333-3333-3333-dddddddddddd";
    private static final String DOTACAO_B = "dddddddd-4444-4444-4444-dddddddddddd";
    private static final String EMPENHO_A = "eeeeeeee-3333-3333-3333-eeeeeeeeeeee";
    private static final String EMPENHO_B = "eeeeeeee-4444-4444-4444-eeeeeeeeeeee";

    @BeforeAll
    static void migraESemeiaDuasEntesComDadosEmTodasAsTabelasProtegidas() throws SQLException {
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
                      ('%s', '33333333333333', 'Ente A', 'municipio'),
                      ('%s', '44444444444444', 'Ente B', 'municipio')
                    """.formatted(ENTE_A, ENTE_B));
            st.execute("""
                    insert into periodo_contabil (id, ente_id, exercicio, mes, status) values
                      ('%s', '%s', 2026, 1, 'aberto'),
                      ('%s', '%s', 2026, 1, 'aberto')
                    """.formatted(PERIODO_A, ENTE_A, PERIODO_B, ENTE_B));
            st.execute("""
                    insert into conta_pcasp (id, ente_id, codigo, descricao, natureza_informacao, natureza_saldo) values
                      ('%s', '%s', '1', 'Ativo', 'patrimonial', 'D'),
                      ('%s', '%s', '1', 'Ativo', 'patrimonial', 'D')
                    """.formatted(CONTA_A, ENTE_A, CONTA_B, ENTE_B));
            st.execute("""
                    insert into fato_contabil (id, ente_id, numero_seq, data_competencia, periodo_id, tipo_evento, historico, origem) values
                      ('%s', '%s', 1, current_date, '%s', 'empenho', 'fato ente A', 'test'),
                      ('%s', '%s', 1, current_date, '%s', 'empenho', 'fato ente B', 'test')
                    """.formatted(FATO_A, ENTE_A, PERIODO_A, FATO_B, ENTE_B, PERIODO_B));
            st.execute("""
                    insert into lancamento (ente_id, fato_id, conta_id, natureza, valor) values
                      ('%s', '%s', '%s', 'D', 100.00),
                      ('%s', '%s', '%s', 'C', 100.00),
                      ('%s', '%s', '%s', 'D', 200.00),
                      ('%s', '%s', '%s', 'C', 200.00)
                    """.formatted(
                    ENTE_A, FATO_A, CONTA_A,
                    ENTE_A, FATO_A, CONTA_A,
                    ENTE_B, FATO_B, CONTA_B,
                    ENTE_B, FATO_B, CONTA_B));
            st.execute("""
                    insert into dotacao (id, ente_id, exercicio, classificacao_orcamentaria, fonte_recurso,
                                          unidade_gestora_id, valor_autorizado) values
                      ('%s', '%s', 2026, '04.122.0001.2001', '0100000000', '11111111-1111-1111-1111-111111111111', 10000.00),
                      ('%s', '%s', 2026, '04.122.0001.2001', '0100000000', '11111111-1111-1111-1111-111111111111', 20000.00)
                    """.formatted(DOTACAO_A, ENTE_A, DOTACAO_B, ENTE_B));
            st.execute("""
                    insert into empenho (id, ente_id, numero_sequencial, exercicio, tipo, dotacao_id, credor_id,
                                         unidade_gestora_id, valor, data_fato, classificacao_orcamentaria,
                                         fonte_recurso, historico, fato_contabil_id) values
                      ('%s', '%s', 1, 2026, 'ordinario', '%s', '22222222-2222-2222-2222-222222222222',
                       '11111111-1111-1111-1111-111111111111', 100.00, current_date, '04.122.0001.2001',
                       '0100000000', 'empenho ente A', '%s'),
                      ('%s', '%s', 1, 2026, 'ordinario', '%s', '22222222-2222-2222-2222-222222222222',
                       '11111111-1111-1111-1111-111111111111', 200.00, current_date, '04.122.0001.2001',
                       '0100000000', 'empenho ente B', '%s')
                    """.formatted(EMPENHO_A, ENTE_A, DOTACAO_A, FATO_A, EMPENHO_B, ENTE_B, DOTACAO_B, FATO_B));
            st.execute("""
                    insert into outbox_mensagem (ente_id, chave, destino, tipo, conteudo) values
                      ('%s', 'chave-a', 'transparencia', 'execucao.empenho.registrado.v1', '{}'),
                      ('%s', 'chave-b', 'transparencia', 'execucao.empenho.registrado.v1', '{}')
                    """.formatted(ENTE_A, ENTE_B));
        }
    }

    /**
     * As tabelas com {@code ente_id} sob RLS forçada às quais o {@code app_role} tem GRANT
     * select — de V1 (razão) e V3/V4 (execução/RAZ-66, outbox/RAZ-9). {@code contador_fato} e
     * {@code contador_empenho} também têm RLS, mas nenhum grant direto (ver javadoc da classe).
     */
    static Stream<String> tabelasProtegidasPorRls() {
        return Stream.of(
                "conta_pcasp", "periodo_contabil", "fato_contabil", "lancamento",
                "dotacao", "empenho", "outbox_mensagem");
    }

    @ParameterizedTest(name = "{0}: soma do que A e B enxergam bate com o total — zero linha cruzada")
    @MethodSource("tabelasProtegidasPorRls")
    void nenhumaLinhaCruzaEntreEntes(String tabela) throws SQLException {
        int totalNoBanco = contarComoAdmin(tabela);
        assertThat(totalNoBanco)
                .as("massa de teste: %s deveria ter pelo menos 1 linha por ente", tabela)
                .isGreaterThanOrEqualTo(2);

        int visivelParaA = contarComoAppLogin(tabela, ENTE_A);
        int visivelParaB = contarComoAppLogin(tabela, ENTE_B);

        // Se A enxergasse qualquer linha de B (ou vice-versa), a soma passaria do total real —
        // essa é a prova de "zero linha cruzada", não apenas "A não vê o fato específico de B".
        assertThat(visivelParaA + visivelParaB)
                .as("%s: soma do que A e B enxergam não pode exceder o total real (vazamento cross-tenant)", tabela)
                .isEqualTo(totalNoBanco);
        assertThat(visivelParaA).as("%s: ente A enxerga ao menos a própria linha", tabela).isGreaterThan(0);
        assertThat(visivelParaB).as("%s: ente B enxerga ao menos a própria linha", tabela).isGreaterThan(0);
    }

    @ParameterizedTest(name = "{0}: sem app.ente_id, RLS nega tudo (deny-by-default)")
    @MethodSource("tabelasProtegidasPorRls")
    void semAppEnteIdNaoRetornaLinhaNenhuma(String tabela) throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login");
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select count(*) from " + tabela)) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("%s: sem app.ente_id definido, deny-by-default deve zerar a consulta mesmo havendo linhas no banco", tabela)
                    .isZero();
        }
    }

    /**
     * {@code saldo_conta} é uma VIEW (não tabela base) com {@code grant select} para
     * {@code app_role} — a única leitura agregada de saldo que a aplicação tem (V1,
     * seção "Saldos"). Uma view comum do Postgres, sem {@code security_invoker = true}
     * (padrão PG15+, ausente na V1), avalia RLS com os privilégios do DONO da view — não
     * de quem consulta. Como a V1 cria a view rodando como o superusuário da migration,
     * {@code force row level security} em {@code lancamento} não se aplica ao dono da
     * view (e superusuário sempre ignora RLS), então {@code saldo_conta} pode vazar TODOS
     * os entes para qualquer sessão de {@code app_login}, mesmo com {@code app.ente_id}
     * setado — reproduzido empiricamente contra a migration V1 real (Postgres, docker):
     * consulta direta em {@code lancamento} respeita a RLS, a mesma sessão consultando
     * {@code saldo_conta} devolve linhas dos dois entes. Teste isolado (fora de {@code
     * tabelasProtegidasPorRls()}) porque é uma superfície de vazamento estruturalmente
     * diferente de RLS em tabela — é exatamente o vazamento cross-tenant que a trava 4
     * do ADR-0003 promete fechar, então fica vermelho até {@code saldo_conta} declarar
     * {@code security_invoker = true} (ou equivalente) na migration.
     */
    @Test
    void saldoContaViewNaoVazaLinhaDeOutroEnte() throws SQLException {
        int totalNoBanco = contarComoAdmin("saldo_conta");
        assertThat(totalNoBanco)
                .as("massa de teste: saldo_conta deveria ter pelo menos 1 linha por ente")
                .isGreaterThanOrEqualTo(2);

        int visivelParaA = contarComoAppLogin("saldo_conta", ENTE_A);
        int visivelParaB = contarComoAppLogin("saldo_conta", ENTE_B);

        assertThat(visivelParaA + visivelParaB)
                .as("saldo_conta: soma do que A e B enxergam não pode exceder o total real "
                        + "(vazamento cross-tenant via VIEW sem security_invoker)")
                .isEqualTo(totalNoBanco);
        assertThat(visivelParaA).as("saldo_conta: ente A enxerga ao menos a própria linha").isGreaterThan(0);
        assertThat(visivelParaB).as("saldo_conta: ente B enxerga ao menos a própria linha").isGreaterThan(0);
    }

    private static int contarComoAdmin(String tabela) throws SQLException {
        try (Connection conn = adminConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select count(*) from " + tabela)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int contarComoAppLogin(String tabela, String enteId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "app_login", "app_login")) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("set local app.ente_id = '" + enteId + "'");
                try (ResultSet rs = st.executeQuery("select count(*) from " + tabela)) {
                    rs.next();
                    int count = rs.getInt(1);
                    conn.commit();
                    return count;
                }
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
