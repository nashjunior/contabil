package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.Dinheiro;
import java.util.Objects;

/** Snapshot de saldo do empenho usado para travar liquidacao <= empenhado. */
public record SaldoEmpenho(EmpenhoId empenhoId, Dinheiro valorEmpenhado, Dinheiro valorLiquidado) {

    public SaldoEmpenho {
        Objects.requireNonNull(empenhoId, "empenhoId não pode ser nulo");
        Objects.requireNonNull(valorEmpenhado, "valorEmpenhado não pode ser nulo");
        Objects.requireNonNull(valorLiquidado, "valorLiquidado não pode ser nulo");
        if (valorEmpenhado.compareTo(Dinheiro.zero()) < 0 || valorLiquidado.compareTo(Dinheiro.zero()) < 0) {
            throw new ExecucaoInvalidaException("saldo_invalido", "saldos do empenho não podem ser negativos");
        }
        if (valorLiquidado.compareTo(valorEmpenhado) > 0) {
            throw new ExecucaoInvalidaException("saldo_invalido", "valor liquidado não pode exceder o empenhado");
        }
    }

    public Dinheiro saldoALiquidar() {
        return valorEmpenhado.subtrair(valorLiquidado);
    }

    public void exigirSaldoParaLiquidar(Dinheiro valor) {
        Objects.requireNonNull(valor, "valor não pode ser nulo");
        if (valor.compareTo(saldoALiquidar()) > 0) {
            throw new SaldoInsuficienteException("liquidação", valor, saldoALiquidar());
        }
    }
}
