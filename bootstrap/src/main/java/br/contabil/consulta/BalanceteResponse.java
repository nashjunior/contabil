package br.contabil.consulta;

import java.util.List;
import java.util.UUID;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.razao.domain.Balancete;
import br.contabil.razao.domain.LinhaBalancete;

/**
 * Corpo HTTP de {@code GET .../razao/balancete} — RAZ-101. Valores são
 * {@link Dinheiro} (serializados como string decimal de 2 casas pelo
 * {@link DinheiroJacksonModule} — RAZ-79 §6.1 / ADR-0030 §2). Cada linha expõe
 * {@code naturezaSaldo} (D/C, a natureza <i>natural</i> da conta PCASP — ADR-0030
 * §5): a UI sinaliza a anomalia "conta devedora com saldo credor" pela natureza
 * da conta, não pelo sinal do {@code saldoAtual}.
 */
record BalanceteResponse(
        int exercicio,
        int mes,
        List<Linha> linhas,
        Dinheiro totalMovimentoDebito,
        Dinheiro totalMovimentoCredito,
        boolean confere) {

    static BalanceteResponse de(Balancete balancete) {
        List<Linha> linhas = balancete.linhas().stream().map(Linha::de).toList();
        return new BalanceteResponse(
                balancete.exercicio(),
                balancete.mes(),
                linhas,
                balancete.totalMovimentoDebito(),
                balancete.totalMovimentoCredito(),
                balancete.confere());
    }

    record Linha(
            UUID contaId,
            String codigo,
            String descricao,
            String naturezaSaldo,
            Dinheiro saldoAnterior,
            Dinheiro movimentoDebito,
            Dinheiro movimentoCredito,
            Dinheiro saldoAtual) {

        static Linha de(LinhaBalancete linha) {
            return new Linha(
                    linha.contaId().valor(),
                    linha.codigo(),
                    linha.descricao(),
                    linha.naturezaSaldo(),
                    linha.saldoAnterior(),
                    linha.movimentoDebito(),
                    linha.movimentoCredito(),
                    linha.saldoAtual());
        }
    }
}
