package br.contabil.razao.infra;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.AnoInscricaoRp;
import br.contabil.razao.domain.ContaContabilId;
import br.contabil.razao.domain.ExecucaoOrcamentaria;
import br.contabil.razao.domain.FonteRecurso;
import br.contabil.razao.domain.FuncaoSubfuncao;
import br.contabil.razao.domain.InformacaoComplementar;
import br.contabil.razao.domain.InformacoesComplementares;
import br.contabil.razao.domain.LinhaMatrizSaldosContabeis;
import br.contabil.razao.domain.LinhaMatrizSaldosContabeisDerivada;
import br.contabil.razao.domain.MatrizSaldosContabeis;
import br.contabil.razao.domain.NaturezaDespesa;
import br.contabil.razao.domain.NaturezaReceita;
import br.contabil.razao.domain.PoderOrgao;
import br.contabil.razao.domain.repository.MatrizSaldosContabeisPort;

/**
 * Deriva a Matriz de Saldos Contábeis (MSC) de um período direto dos lançamentos
 * (razao-contabil-schema.md §Saldos), extensão de {@link PostgresBalancetePort} pela
 * dimensão das informações complementares (IC, ADR-0056/ADR-0057): mesma máquina de
 * saldo anterior/movimento D/movimento C/saldo atual por período, agora agrupada também
 * por {@code fonte_recurso, execucao_orcamentaria, natureza_receita, natureza_despesa,
 * funcao_subfuncao, ano_inscricao_rp} (dimensão de partida, {@code lancamento}) e
 * {@code poder_orgao} (dimensão de fato, {@code fato_contabil}). Só contas escrituráveis
 * com algum valor não-zero aparecem, mesmo princípio de {@link PostgresBalancetePort}.
 *
 * <p><b>FP (superávit financeiro)</b> é DERIVADO reusando a DDR "Recursos Disponíveis"
 * por {@link FonteRecurso} ({@code 8.2.1.1.1.01.00}/{@code .02.00}, os mesmos códigos de
 * {@link ParametrosTransposicaoDdrAberturaOficiais} — IPC 03/STN §96) — não ganha coluna
 * própria (ADR-0007/ADR-0057): é rótulo sobre um saldo que já aparece como linha capturada
 * da conta DDR de origem, por isso {@link MatrizSaldosContabeis#confere()} não soma as
 * linhas derivadas. Contas FP ainda não cadastradas no ente resolvem para "sem FP" (esta é
 * uma leitura, {@code Acao.LER} — diferente de {@code ParametrosEncerramentoDdrOficiais},
 * que EXIGE a conta por ser usada num encerramento de escrita).
 *
 * <p><b>DC (dívida consolidada)</b> ainda não é derivada — nenhuma conta PCASP de dívida
 * consolidada está mapeada no MCASP vigente pela documentação do produto
 * (docs/16-prestacao-de-contas.md item 1); ao contrário da FP, DC não é um saldo DDR
 * (classes 7/8) e sim patrimonial (LRF art. 29), o que exige pesquisa própria de fonte
 * antes de inventar um código PCASP. {@code [REVALIDAR]} — rastreado como issue filha.
 */
@Component
public class PostgresMatrizSaldosContabeisPort implements MatrizSaldosContabeisPort {

    /**
     * DDR "Recursos Disponíveis para o Exercício" / "Recursos de Exercícios Anteriores"
     * (IPC 03/STN rev. 2017 §96) — mesmos códigos de
     * {@link ParametrosTransposicaoDdrAberturaOficiais}. É o saldo que a MSC rotula
     * {@code FP} por fonte. {@code [REVALIDAR]} contra o MCASP edição vigente.
     */
    private static final List<String> CODIGOS_FP = List.of("8.2.1.1.1.01.00", "8.2.1.1.1.02.00");

    private static final String SQL_BASE =
            """
            with periodo as (
              select id, exercicio, mes from periodo_contabil where ente_id = ? and exercicio = ? and mes = ?
            ),
            base as (
              select l.conta_id,
                     l.fonte_recurso, l.execucao_orcamentaria, l.natureza_receita,
                     l.natureza_despesa, l.funcao_subfuncao, l.ano_inscricao_rp,
                     f.poder_orgao,
                     sum(l.valor) filter (where l.natureza = 'D' and f.periodo_id = (select id from periodo)) as movimento_debito,
                     sum(l.valor) filter (where l.natureza = 'C' and f.periodo_id = (select id from periodo)) as movimento_credito,
                     sum(case when l.natureza = 'D' then l.valor else -l.valor end)
                       filter (where (p.exercicio, p.mes) < (select exercicio, mes from periodo)) as saldo_anterior
                from lancamento l
                join fato_contabil f on f.ente_id = l.ente_id and f.id = l.fato_id
                join periodo_contabil p on p.ente_id = f.ente_id and p.id = f.periodo_id
               where l.ente_id = ?
               group by l.conta_id, l.fonte_recurso, l.execucao_orcamentaria, l.natureza_receita,
                        l.natureza_despesa, l.funcao_subfuncao, l.ano_inscricao_rp, f.poder_orgao
            )
            select c.id as conta_id, c.codigo, c.descricao, c.natureza_saldo,
                   b.fonte_recurso, b.execucao_orcamentaria, b.natureza_receita,
                   b.natureza_despesa, b.funcao_subfuncao, b.ano_inscricao_rp, b.poder_orgao,
                   coalesce(b.saldo_anterior, 0) as saldo_anterior,
                   coalesce(b.movimento_debito, 0) as movimento_debito,
                   coalesce(b.movimento_credito, 0) as movimento_credito
              from base b
              join conta_pcasp c on c.id = b.conta_id and c.ente_id = ?
             where c.escrituravel = true
               and (coalesce(b.saldo_anterior, 0) <> 0 or coalesce(b.movimento_debito, 0) <> 0 or coalesce(b.movimento_credito, 0) <> 0)
             order by c.codigo
            """;

    private static final String SQL_RESOLVER_CONTA_FP =
            "select id from conta_pcasp where ente_id = ? and codigo = ?";

    private static final String SQL_FP =
            """
            with periodo as (
              select id, exercicio, mes from periodo_contabil where ente_id = ? and exercicio = ? and mes = ?
            ),
            base as (
              select l.fonte_recurso,
                     sum(l.valor) filter (where l.natureza = 'D' and f.periodo_id = (select id from periodo)) as movimento_debito,
                     sum(l.valor) filter (where l.natureza = 'C' and f.periodo_id = (select id from periodo)) as movimento_credito,
                     sum(case when l.natureza = 'D' then l.valor else -l.valor end)
                       filter (where (p.exercicio, p.mes) < (select exercicio, mes from periodo)) as saldo_anterior
                from lancamento l
                join fato_contabil f on f.ente_id = l.ente_id and f.id = l.fato_id
                join periodo_contabil p on p.ente_id = f.ente_id and p.id = f.periodo_id
               where l.ente_id = ?
                 and l.conta_id = any(?)
                 and l.fonte_recurso is not null
               group by l.fonte_recurso
            )
            select fonte_recurso,
                   coalesce(saldo_anterior, 0) as saldo_anterior,
                   coalesce(movimento_debito, 0) as movimento_debito,
                   coalesce(movimento_credito, 0) as movimento_credito
              from base
             where coalesce(saldo_anterior, 0) <> 0 or coalesce(movimento_debito, 0) <> 0 or coalesce(movimento_credito, 0) <> 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresMatrizSaldosContabeisPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MatrizSaldosContabeis matriz(TenantId enteId, int exercicio, int mes) {
        List<LinhaMatrizSaldosContabeis> linhas = jdbcTemplate.query(
                SQL_BASE,
                (rs, rowNum) -> mapearLinha(rs),
                enteId.valor(), exercicio, mes, enteId.valor(), enteId.valor());

        List<LinhaMatrizSaldosContabeisDerivada> derivadas = derivarFp(enteId, exercicio, mes);

        return new MatrizSaldosContabeis(enteId, exercicio, mes, linhas, derivadas);
    }

    private LinhaMatrizSaldosContabeis mapearLinha(ResultSet rs) throws SQLException {
        String fonteRecurso = rs.getString("fonte_recurso");
        String execucaoOrcamentaria = rs.getString("execucao_orcamentaria");
        String naturezaReceita = rs.getString("natureza_receita");
        String naturezaDespesa = rs.getString("natureza_despesa");
        String funcaoSubfuncao = rs.getString("funcao_subfuncao");
        int anoInscricaoRp = rs.getInt("ano_inscricao_rp");
        boolean temAnoInscricaoRp = !rs.wasNull();
        String poderOrgao = rs.getString("poder_orgao");

        Dinheiro saldoAnterior = new Dinheiro(rs.getBigDecimal("saldo_anterior"));
        Dinheiro movimentoDebito = new Dinheiro(rs.getBigDecimal("movimento_debito"));
        Dinheiro movimentoCredito = new Dinheiro(rs.getBigDecimal("movimento_credito"));
        Dinheiro saldoAtual = saldoAnterior.somar(movimentoDebito).subtrair(movimentoCredito);

        return new LinhaMatrizSaldosContabeis(
                new ContaContabilId(rs.getObject("conta_id", UUID.class)),
                rs.getString("codigo"),
                rs.getString("descricao"),
                rs.getString("natureza_saldo"),
                poderOrgao == null ? null : PoderOrgao.de(poderOrgao),
                InformacoesComplementares.de(
                        fonteRecurso == null ? null : FonteRecurso.de(fonteRecurso),
                        execucaoOrcamentaria == null ? null : ExecucaoOrcamentaria.de(execucaoOrcamentaria),
                        naturezaReceita == null ? null : NaturezaReceita.de(naturezaReceita),
                        naturezaDespesa == null ? null : NaturezaDespesa.de(naturezaDespesa),
                        funcaoSubfuncao == null ? null : FuncaoSubfuncao.de(funcaoSubfuncao),
                        temAnoInscricaoRp ? AnoInscricaoRp.de(anoInscricaoRp) : null),
                saldoAnterior,
                movimentoDebito,
                movimentoCredito,
                saldoAtual);
    }

    private List<LinhaMatrizSaldosContabeisDerivada> derivarFp(TenantId enteId, int exercicio, int mes) {
        UUID[] contasFp = CODIGOS_FP.stream()
                .map(codigo -> resolverContaFp(enteId, codigo))
                .filter(Objects::nonNull)
                .toArray(UUID[]::new);
        if (contasFp.length == 0) {
            return List.of();
        }

        return jdbcTemplate.execute(
                (Connection conn) -> conn.prepareStatement(SQL_FP),
                (PreparedStatement ps) -> {
                    ps.setObject(1, enteId.valor());
                    ps.setInt(2, exercicio);
                    ps.setInt(3, mes);
                    ps.setObject(4, enteId.valor());
                    Array array = ps.getConnection().createArrayOf("uuid", contasFp);
                    ps.setArray(5, array);
                    List<LinhaMatrizSaldosContabeisDerivada> resultado = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Dinheiro saldoAnterior = new Dinheiro(rs.getBigDecimal("saldo_anterior"));
                            Dinheiro movimentoDebito = new Dinheiro(rs.getBigDecimal("movimento_debito"));
                            Dinheiro movimentoCredito = new Dinheiro(rs.getBigDecimal("movimento_credito"));
                            Dinheiro saldoAtual = saldoAnterior.somar(movimentoDebito).subtrair(movimentoCredito);
                            resultado.add(new LinhaMatrizSaldosContabeisDerivada(
                                    InformacaoComplementar.FP,
                                    FonteRecurso.de(rs.getString("fonte_recurso")),
                                    saldoAnterior,
                                    movimentoDebito,
                                    movimentoCredito,
                                    saldoAtual));
                        }
                    }
                    return resultado;
                });
    }

    private UUID resolverContaFp(TenantId enteId, String codigoPcasp) {
        List<UUID> ids = jdbcTemplate.query(
                SQL_RESOLVER_CONTA_FP, (rs, rowNum) -> rs.getObject("id", UUID.class), enteId.valor(), codigoPcasp);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
