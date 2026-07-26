package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.Dinheiro;
import java.util.Objects;

/** Snapshot de saldo da dotação usado para travar empenho <= crédito (Lei 4.320 art. 59). */
public record SaldoDotacao(DotacaoId dotacaoId, Dinheiro valorAutorizado, Dinheiro valorComprometido) {

    public SaldoDotacao {
        Objects.requireNonNull(dotacaoId, "dotacaoId não pode ser nulo");
        Objects.requireNonNull(valorAutorizado, "valorAutorizado não pode ser nulo");
        Objects.requireNonNull(valorComprometido, "valorComprometido não pode ser nulo");
        if (valorAutorizado.compareTo(Dinheiro.zero()) < 0 || valorComprometido.compareTo(Dinheiro.zero()) < 0) {
            throw new ExecucaoInvalidaException("saldo_invalido", "saldos da dotação não podem ser negativos");
        }
        if (valorComprometido.compareTo(valorAutorizado) > 0) {
            throw new ExecucaoInvalidaException("saldo_invalido", "valor comprometido não pode exceder o autorizado");
        }
    }

    public Dinheiro saldoDisponivel() {
        return valorAutorizado.subtrair(valorComprometido);
    }

    public void exigirSaldoParaComprometer(Dinheiro valor) {
        Objects.requireNonNull(valor, "valor não pode ser nulo");
        if (valor.compareTo(saldoDisponivel()) > 0) {
            throw new SaldoInsuficienteException("empenho", valor, saldoDisponivel());
        }
    }
}
