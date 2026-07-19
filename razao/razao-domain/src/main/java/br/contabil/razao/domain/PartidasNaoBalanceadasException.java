package br.contabil.razao.domain;

import br.contabil.plataforma.domain.Dinheiro;

/**
 * Regra 8 (05-regras-de-negocio.md): a soma dos lançamentos a débito precisa
 * fechar exatamente com a soma dos lançamentos a crédito, por fato. Verificada
 * na aplicação (aqui) antes de qualquer I/O — o constraint trigger diferido do
 * banco (razao-contabil-schema.md) é a rede de segurança, não a checagem primária.
 */
public class PartidasNaoBalanceadasException extends RuntimeException {

    public PartidasNaoBalanceadasException(Dinheiro somaDebito, Dinheiro somaCredito) {
        super("Partidas dobradas violadas: soma(D)=%s soma(C)=%s".formatted(somaDebito, somaCredito));
    }

    public PartidasNaoBalanceadasException(String motivo) {
        super(motivo);
    }
}
