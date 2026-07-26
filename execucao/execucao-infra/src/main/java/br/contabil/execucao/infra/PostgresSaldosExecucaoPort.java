package br.contabil.execucao.infra;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.contabil.execucao.domain.DotacaoId;
import br.contabil.execucao.domain.EmpenhoId;
import br.contabil.execucao.domain.ExecucaoInvalidaException;
import br.contabil.execucao.domain.LiquidacaoId;
import br.contabil.execucao.domain.SaldoDotacao;
import br.contabil.execucao.domain.SaldoEmpenho;
import br.contabil.execucao.domain.SaldoLiquidacao;
import br.contabil.execucao.domain.repository.SaldosExecucaoPort;
import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.TenantId;

/**
 * Adapter Postgres dos saldos operacionais da execução (execucao-orcamentaria-despesa.md
 * §Dois saldos, dois donos): o saldo é DERIVADO (soma dos registros filhos já
 * persistidos), nunca uma coluna mutada — a trava de concorrência (empenho <=
 * crédito) vem do {@code select ... for update} na linha da {@code dotacao},
 * que serializa duas transações concorrentes sobre a mesma dotação até o
 * commit da primeira.
 */
@Component
public class PostgresSaldosExecucaoPort implements SaldosExecucaoPort {

    private static final String SQL_LOCK_DOTACAO =
            "select valor_autorizado from dotacao where ente_id = ? and id = ? for update";

    private static final String SQL_SOMA_EMPENHOS_DA_DOTACAO =
            "select coalesce(sum(valor), 0) from empenho where ente_id = ? and dotacao_id = ?";

    private static final String SQL_LOCK_EMPENHO =
            "select valor from empenho where ente_id = ? and id = ? for update";

    private static final String SQL_SOMA_LIQUIDACOES_DO_EMPENHO =
            "select coalesce(sum(valor), 0) from liquidacao where ente_id = ? and empenho_id = ?";

    private static final String SQL_LOCK_LIQUIDACAO =
            "select valor from liquidacao where ente_id = ? and id = ? for update";

    private static final String SQL_SOMA_PAGAMENTOS_DA_LIQUIDACAO =
            "select coalesce(sum(valor), 0) from pagamento where ente_id = ? and liquidacao_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public PostgresSaldosExecucaoPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SaldoDotacao saldoDotacao(TenantId enteId, DotacaoId dotacaoId) {
        List<BigDecimal> linhas = jdbcTemplate.query(
                SQL_LOCK_DOTACAO,
                (rs, rowNum) -> rs.getBigDecimal("valor_autorizado"),
                enteId.valor(),
                dotacaoId.valor());
        if (linhas.isEmpty()) {
            throw new ExecucaoInvalidaException(
                    "dotacao_nao_encontrada", "dotação %s não encontrada para o ente".formatted(dotacaoId));
        }
        BigDecimal valorComprometido = jdbcTemplate.queryForObject(
                SQL_SOMA_EMPENHOS_DA_DOTACAO, BigDecimal.class, enteId.valor(), dotacaoId.valor());
        return new SaldoDotacao(dotacaoId, new Dinheiro(linhas.get(0)), new Dinheiro(valorComprometido));
    }

    @Override
    public SaldoEmpenho saldoEmpenho(TenantId enteId, EmpenhoId empenhoId) {
        List<BigDecimal> linhas = jdbcTemplate.query(
                SQL_LOCK_EMPENHO,
                (rs, rowNum) -> rs.getBigDecimal("valor"),
                enteId.valor(),
                empenhoId.valor());
        if (linhas.isEmpty()) {
            throw new ExecucaoInvalidaException(
                    "empenho_nao_encontrado", "empenho %s não encontrado para o ente".formatted(empenhoId));
        }
        BigDecimal valorLiquidado = jdbcTemplate.queryForObject(
                SQL_SOMA_LIQUIDACOES_DO_EMPENHO, BigDecimal.class, enteId.valor(), empenhoId.valor());
        return new SaldoEmpenho(empenhoId, new Dinheiro(linhas.get(0)), new Dinheiro(valorLiquidado));
    }

    @Override
    public SaldoLiquidacao saldoLiquidacao(TenantId enteId, LiquidacaoId liquidacaoId) {
        List<BigDecimal> linhas = jdbcTemplate.query(
                SQL_LOCK_LIQUIDACAO,
                (rs, rowNum) -> rs.getBigDecimal("valor"),
                enteId.valor(),
                liquidacaoId.valor());
        if (linhas.isEmpty()) {
            throw new ExecucaoInvalidaException(
                    "liquidacao_nao_encontrada",
                    "liquidação %s não encontrada para o ente".formatted(liquidacaoId));
        }
        BigDecimal valorPago = jdbcTemplate.queryForObject(
                SQL_SOMA_PAGAMENTOS_DA_LIQUIDACAO, BigDecimal.class, enteId.valor(), liquidacaoId.valor());
        return new SaldoLiquidacao(liquidacaoId, new Dinheiro(linhas.get(0)), new Dinheiro(valorPago));
    }
}
