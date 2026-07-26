package br.contabil.razao.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.LinhaBalancete;
import br.contabil.razao.domain.repository.BalancetePort;

/**
 * Deriva o balancete de um período direto dos lançamentos (razao-contabil-schema.md
 * §Saldos), sem tabela de saldo materializada: {@code saldo_anterior} soma tudo até
 * o período imediatamente anterior (comparação lexicográfica de
 * {@code (exercicio, mes)} — o mês 13 de encerramento fica corretamente depois do
 * mês 12 do mesmo exercício e antes do mês 1 do exercício seguinte); movimento D/C
 * soma só os lançamentos cujo fato caiu no {@code periodo_id} pedido. Só contas
 * <b>escrituráveis</b> (analíticas — as únicas que recebem lançamento) e com algum
 * valor não-zero aparecem, para não poluir o relatório com o plano de contas
 * inteiro. {@code exercicio}/{@code mes} sem {@code periodo_contabil} cadastrado
 * resolve para um balancete de linhas vazias (mesmo princípio "derivado, zero não é
 * erro" de {@link PostgresConsultaSaldoPort}).
 */
@Component
public class PostgresBalancetePort implements BalancetePort {

    private static final String SQL_BALANCETE =
            """
            with periodo as (
              select id, exercicio, mes from periodo_contabil where ente_id = ? and exercicio = ? and mes = ?
            ),
            movimento as (
              select l.conta_id,
                     sum(l.valor) filter (where l.natureza = 'D') as debito,
                     sum(l.valor) filter (where l.natureza = 'C') as credito
                from lancamento l
                join fato_contabil f on f.ente_id = l.ente_id and f.id = l.fato_id
               where l.ente_id = ? and f.periodo_id = (select id from periodo)
               group by l.conta_id
            ),
            anterior as (
              select l.conta_id,
                     sum(case when l.natureza = 'D' then l.valor else -l.valor end) as saldo
                from lancamento l
                join fato_contabil f on f.ente_id = l.ente_id and f.id = l.fato_id
                join periodo_contabil p on p.ente_id = f.ente_id and p.id = f.periodo_id
               where l.ente_id = ? and (p.exercicio, p.mes) < (select exercicio, mes from periodo)
               group by l.conta_id
            )
            select c.id as conta_id, c.codigo, c.descricao,
                   coalesce(a.saldo, 0) as saldo_anterior,
                   coalesce(m.debito, 0) as movimento_debito,
                   coalesce(m.credito, 0) as movimento_credito
              from conta_pcasp c
              left join movimento m on m.conta_id = c.id
              left join anterior a on a.conta_id = c.id
             where c.ente_id = ? and c.escrituravel = true
               and (coalesce(a.saldo, 0) <> 0 or coalesce(m.debito, 0) <> 0 or coalesce(m.credito, 0) <> 0)
             order by c.codigo
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresBalancetePort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Balancete balancete(TenantId enteId, int exercicio, int mes) {
        List<LinhaBalancete> linhas = jdbcTemplate.query(
                SQL_BALANCETE,
                (rs, rowNum) -> {
                    Dinheiro saldoAnterior = new Dinheiro(rs.getBigDecimal("saldo_anterior"));
                    Dinheiro movimentoDebito = new Dinheiro(rs.getBigDecimal("movimento_debito"));
                    Dinheiro movimentoCredito = new Dinheiro(rs.getBigDecimal("movimento_credito"));
                    Dinheiro saldoAtual = saldoAnterior.somar(movimentoDebito).subtrair(movimentoCredito);
                    return new LinhaBalancete(
                            rs.getObject("conta_id", UUID.class),
                            rs.getString("codigo"),
                            rs.getString("descricao"),
                            saldoAnterior,
                            movimentoDebito,
                            movimentoCredito,
                            saldoAtual);
                },
                enteId.valor(),
                exercicio,
                mes,
                enteId.valor(),
                enteId.valor(),
                enteId.valor());
        return new Balancete(enteId, exercicio, mes, linhas);
    }
}
