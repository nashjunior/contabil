package br.contabil.razao.domain;

import br.contabil.plataforma.domain.ErroContrato;

/**
 * Tentativa de cancelar um fato que não é uma inscrição de Restos a Pagar
 * ({@link TipoEvento#INSCRICAO_RESTOS_A_PAGAR}).
 */
public final class CancelamentoRestosAPagarInvalidoException extends RuntimeException implements ErroContrato {

    public CancelamentoRestosAPagarInvalidoException(FatoContabilId fatoId, TipoEvento tipoEvento) {
        super("fato %s não é uma inscrição de RP (tipo: %s)".formatted(fatoId, tipoEvento));
    }

    @Override
    public String codigo() {
        return "cancelamento_restos_a_pagar_invalido";
    }
}
