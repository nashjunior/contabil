package br.contabil.consulta;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.LinhaBalancete;

/** Corpo HTTP de {@code GET .../razao/balancete} — RAZ-101. */
record BalanceteResponse(
        int exercicio,
        int mes,
        List<Linha> linhas,
        BigDecimal totalMovimentoDebito,
        BigDecimal totalMovimentoCredito,
        boolean confere) {

    static BalanceteResponse de(Balancete balancete) {
        List<Linha> linhas = balancete.linhas().stream().map(Linha::de).toList();
        return new BalanceteResponse(
                balancete.exercicio(),
                balancete.mes(),
                linhas,
                balancete.totalMovimentoDebito().valor(),
                balancete.totalMovimentoCredito().valor(),
                balancete.confere());
    }

    record Linha(
            UUID contaId,
            String codigo,
            String descricao,
            BigDecimal saldoAnterior,
            BigDecimal movimentoDebito,
            BigDecimal movimentoCredito,
            BigDecimal saldoAtual) {

        static Linha de(LinhaBalancete linha) {
            return new Linha(
                    linha.contaId(),
                    linha.codigo(),
                    linha.descricao(),
                    linha.saldoAnterior().valor(),
                    linha.movimentoDebito().valor(),
                    linha.movimentoCredito().valor(),
                    linha.saldoAtual().valor());
        }
    }
}
