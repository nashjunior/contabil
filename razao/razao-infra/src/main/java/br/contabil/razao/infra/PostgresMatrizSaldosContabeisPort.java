package br.contabil.razao.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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
import br.contabil.razao.domain.InformacoesComplementares;
import br.contabil.razao.domain.LinhaMatrizSaldos;
import br.contabil.razao.domain.NaturezaDespesa;
import br.contabil.razao.domain.NaturezaReceita;
import br.contabil.razao.domain.PoderOrgao;
import br.contabil.razao.domain.repository.MatrizSaldosContabeisPort;

/**
 * Deriva a MSC (Matriz de Saldos Contábeis) direto dos lançamentos, estendendo o
 * mesmo motor de saldo por período de {@link PostgresBalancetePort} (ADR-0056)
 * pela dimensão das informações complementares (IC): {@code execucao_orcamentaria,
 * natureza_receita, natureza_despesa, funcao_subfuncao, ano_inscricao_rp,
 * fonte_recurso} (colunas de partida, {@code lancamento}) e {@code poder_orgao}
 * (fato-wide, {@code fato_contabil}) — colunas nascidas na RAZ-267/RAZ-225 (ADR-0057/
 * ADR-0054). Cada combinação distinta de conta×IC vira uma linha própria; como
 * {@code NULL} agrupa junto no Postgres, contas sem nenhuma IC capturada (a maioria
 * do plano — patrimoniais/orçamentárias fora da DDR) caem numa única linha "sem IC",
 * preservando o comportamento de {@link PostgresBalancetePort} para o caso comum.
 *
 * <p>As CTEs {@code movimento}/{@code anterior} agregam por período (mesma
 * comparação lexicográfica {@code (exercicio, mes)} do balancete — o mês 13 de
 * encerramento fica depois do mês 12 e antes do mês 1 seguinte); {@code chaves}
 * une as combinações vistas em qualquer um dos dois lados (uma conta×IC pode ter
 * saldo anterior sem movimento no período, ou vice-versa); o join final usa
 * {@code IS NOT DISTINCT FROM} porque as colunas de IC são nullable e a igualdade
 * SQL comum (`=`) nunca casa `NULL = NULL`. Só contas escrituráveis com algum
 * valor não-zero aparecem (mesmo filtro do balancete, para não poluir a MSC com o
 * plano de contas inteiro).
 */
@Component
public class PostgresMatrizSaldosContabeisPort implements MatrizSaldosContabeisPort {

    private static final String SQL_MATRIZ =
            """
            with periodo as (
              select id, exercicio, mes from periodo_contabil where ente_id = ? and exercicio = ? and mes = ?
            ),
            movimento as (
              select l.conta_id, l.execucao_orcamentaria, l.natureza_receita, l.natureza_despesa,
                     l.funcao_subfuncao, l.ano_inscricao_rp, l.fonte_recurso, f.poder_orgao,
                     sum(l.valor) filter (where l.natureza = 'D') as debito,
                     sum(l.valor) filter (where l.natureza = 'C') as credito
                from lancamento l
                join fato_contabil f on f.ente_id = l.ente_id and f.id = l.fato_id
               where l.ente_id = ? and f.periodo_id = (select id from periodo)
               group by l.conta_id, l.execucao_orcamentaria, l.natureza_receita, l.natureza_despesa,
                        l.funcao_subfuncao, l.ano_inscricao_rp, l.fonte_recurso, f.poder_orgao
            ),
            anterior as (
              select l.conta_id, l.execucao_orcamentaria, l.natureza_receita, l.natureza_despesa,
                     l.funcao_subfuncao, l.ano_inscricao_rp, l.fonte_recurso, f.poder_orgao,
                     sum(case when l.natureza = 'D' then l.valor else -l.valor end) as saldo
                from lancamento l
                join fato_contabil f on f.ente_id = l.ente_id and f.id = l.fato_id
                join periodo_contabil p on p.ente_id = f.ente_id and p.id = f.periodo_id
               where l.ente_id = ? and (p.exercicio, p.mes) < (select exercicio, mes from periodo)
               group by l.conta_id, l.execucao_orcamentaria, l.natureza_receita, l.natureza_despesa,
                        l.funcao_subfuncao, l.ano_inscricao_rp, l.fonte_recurso, f.poder_orgao
            ),
            chaves as (
              select conta_id, execucao_orcamentaria, natureza_receita, natureza_despesa,
                     funcao_subfuncao, ano_inscricao_rp, fonte_recurso, poder_orgao from movimento
              union
              select conta_id, execucao_orcamentaria, natureza_receita, natureza_despesa,
                     funcao_subfuncao, ano_inscricao_rp, fonte_recurso, poder_orgao from anterior
            )
            select c.id as conta_id, c.codigo, c.descricao, c.natureza_saldo,
                   k.execucao_orcamentaria, k.natureza_receita, k.natureza_despesa,
                   k.funcao_subfuncao, k.ano_inscricao_rp, k.fonte_recurso, k.poder_orgao,
                   coalesce(a.saldo, 0) as saldo_anterior,
                   coalesce(m.debito, 0) as movimento_debito,
                   coalesce(m.credito, 0) as movimento_credito
              from chaves k
              join conta_pcasp c on c.id = k.conta_id
              left join movimento m
                on m.conta_id = k.conta_id
               and m.execucao_orcamentaria is not distinct from k.execucao_orcamentaria
               and m.natureza_receita is not distinct from k.natureza_receita
               and m.natureza_despesa is not distinct from k.natureza_despesa
               and m.funcao_subfuncao is not distinct from k.funcao_subfuncao
               and m.ano_inscricao_rp is not distinct from k.ano_inscricao_rp
               and m.fonte_recurso is not distinct from k.fonte_recurso
               and m.poder_orgao is not distinct from k.poder_orgao
              left join anterior a
                on a.conta_id = k.conta_id
               and a.execucao_orcamentaria is not distinct from k.execucao_orcamentaria
               and a.natureza_receita is not distinct from k.natureza_receita
               and a.natureza_despesa is not distinct from k.natureza_despesa
               and a.funcao_subfuncao is not distinct from k.funcao_subfuncao
               and a.ano_inscricao_rp is not distinct from k.ano_inscricao_rp
               and a.fonte_recurso is not distinct from k.fonte_recurso
               and a.poder_orgao is not distinct from k.poder_orgao
             where c.ente_id = ? and c.escrituravel = true
               and (coalesce(a.saldo, 0) <> 0 or coalesce(m.debito, 0) <> 0 or coalesce(m.credito, 0) <> 0)
             order by c.codigo
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresMatrizSaldosContabeisPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<LinhaMatrizSaldos> matriz(TenantId enteId, int exercicio, int mes) {
        return jdbcTemplate.query(
                SQL_MATRIZ,
                PostgresMatrizSaldosContabeisPort::mapearLinha,
                enteId.valor(),
                exercicio,
                mes,
                enteId.valor(),
                enteId.valor(),
                enteId.valor());
    }

    private static LinhaMatrizSaldos mapearLinha(ResultSet rs, int rowNum) throws SQLException {
        Dinheiro saldoAnterior = new Dinheiro(rs.getBigDecimal("saldo_anterior"));
        Dinheiro movimentoDebito = new Dinheiro(rs.getBigDecimal("movimento_debito"));
        Dinheiro movimentoCredito = new Dinheiro(rs.getBigDecimal("movimento_credito"));
        Dinheiro saldoAtual = saldoAnterior.somar(movimentoDebito).subtrair(movimentoCredito);
        InformacoesComplementares ic = InformacoesComplementares.de(
                fonteRecurso(rs.getString("fonte_recurso")),
                execucaoOrcamentaria(rs.getString("execucao_orcamentaria")),
                naturezaReceita(rs.getString("natureza_receita")),
                naturezaDespesa(rs.getString("natureza_despesa")),
                funcaoSubfuncao(rs.getString("funcao_subfuncao")),
                anoInscricaoRp(rs));
        return new LinhaMatrizSaldos(
                new ContaContabilId(rs.getObject("conta_id", UUID.class)),
                rs.getString("codigo"),
                rs.getString("descricao"),
                rs.getString("natureza_saldo"),
                ic,
                poderOrgao(rs.getString("poder_orgao")),
                saldoAnterior,
                movimentoDebito,
                movimentoCredito,
                saldoAtual);
    }

    private static PoderOrgao poderOrgao(String codigo) {
        return codigo == null ? null : PoderOrgao.de(codigo);
    }

    private static FonteRecurso fonteRecurso(String codigo) {
        return codigo == null ? null : FonteRecurso.de(codigo);
    }

    private static ExecucaoOrcamentaria execucaoOrcamentaria(String codigo) {
        return codigo == null ? null : ExecucaoOrcamentaria.de(codigo);
    }

    private static NaturezaReceita naturezaReceita(String codigo) {
        return codigo == null ? null : NaturezaReceita.de(codigo);
    }

    private static NaturezaDespesa naturezaDespesa(String codigo) {
        return codigo == null ? null : NaturezaDespesa.de(codigo);
    }

    private static FuncaoSubfuncao funcaoSubfuncao(String codigo) {
        return codigo == null ? null : FuncaoSubfuncao.de(codigo);
    }

    private static AnoInscricaoRp anoInscricaoRp(ResultSet rs) throws SQLException {
        int ano = rs.getInt("ano_inscricao_rp");
        return rs.wasNull() ? null : AnoInscricaoRp.de(ano);
    }
}
