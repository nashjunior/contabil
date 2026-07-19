package br.contabil.razao.domain;

/**
 * Lançamento fora das travas do razão: valor não-positivo ou natureza fora de
 * {D,C} (mesma trava do {@code check} no banco, espelhada no VO de domínio).
 */
public class LancamentoInvalidoException extends RuntimeException {

    public LancamentoInvalidoException(String motivo) {
        super(motivo);
    }
}
