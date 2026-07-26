package br.contabil.execucao.domain;

import br.contabil.plataforma.domain.Dinheiro;
import br.contabil.plataforma.domain.Validacoes;

/** Snapshot de saldo da dotação usado para travar empenho <= crédito (Lei 4.320 art. 59). */
public record SaldoDotacao(DotacaoId dotacaoId, Dinheiro valorAutorizado, Dinheiro valorComprometido) {

    public SaldoDotacao {
        Validacoes.exigirNaoNulo(dotacaoId, "dotacaoId");
        Validacoes.exigirNaoNulo(valorAutorizado, "valorAutorizado");
        Validacoes.exigirNaoNulo(valorComprometido, "valorComprometido");
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
        Validacoes.exigirNaoNulo(valor, "valor");
        if (valor.compareTo(saldoDisponivel()) > 0) {
            throw new SaldoInsuficienteException("empenho", valor, saldoDisponivel());
        }
    }
}
