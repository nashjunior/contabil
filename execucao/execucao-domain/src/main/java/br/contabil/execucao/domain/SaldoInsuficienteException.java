package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.Dinheiro;

/** Valor solicitado excede o saldo disponível do estágio anterior. */
public final class SaldoInsuficienteException extends ExecucaoInvalidaException {

    public SaldoInsuficienteException(String estagio, Dinheiro solicitado, Dinheiro disponivel) {
        super(
                "saldo_insuficiente",
                "%s solicitado (%s) excede o saldo disponível (%s)".formatted(estagio, solicitado, disponivel));
    }
}
