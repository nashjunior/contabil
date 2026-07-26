package br.contabil.execucao.infra;

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
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
        throw new UnsupportedOperationException(
                "RAZ-67: saldo do empenho (soma das liquidações) ainda não implementado nesta infra");
    }

    @Override
    public SaldoLiquidacao saldoLiquidacao(TenantId enteId, LiquidacaoId liquidacaoId) {
        throw new UnsupportedOperationException(
                "RAZ-67: saldo da liquidação (soma dos pagamentos) ainda não implementado nesta infra");
    }
}
