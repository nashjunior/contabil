package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.Dinheiro;
import java.util.Objects;

/** Snapshot de saldo da liquidação usado para travar pagamento <= liquidado. */
public record SaldoLiquidacao(LiquidacaoId liquidacaoId, Dinheiro valorLiquidado, Dinheiro valorPago) {

    public SaldoLiquidacao {
        Objects.requireNonNull(liquidacaoId, "liquidacaoId não pode ser nulo");
        Objects.requireNonNull(valorLiquidado, "valorLiquidado não pode ser nulo");
        Objects.requireNonNull(valorPago, "valorPago não pode ser nulo");
        if (valorLiquidado.compareTo(Dinheiro.zero()) < 0 || valorPago.compareTo(Dinheiro.zero()) < 0) {
            throw new ExecucaoInvalidaException("saldo_invalido", "saldos da liquidação não podem ser negativos");
        }
        if (valorPago.compareTo(valorLiquidado) > 0) {
            throw new ExecucaoInvalidaException("saldo_invalido", "valor pago não pode exceder o liquidado");
        }
    }

    public Dinheiro saldoAPagar() {
        return valorLiquidado.subtrair(valorPago);
    }

    public void exigirSaldoParaPagar(Dinheiro valor) {
        Objects.requireNonNull(valor, "valor não pode ser nulo");
        if (valor.compareTo(saldoAPagar()) > 0) {
            throw new SaldoInsuficienteException("pagamento", valor, saldoAPagar());
        }
    }
}
