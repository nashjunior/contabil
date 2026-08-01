package br.contabil.razao.domain;

import br.contabil.plataforma.domain.ErroContrato;

/**
 * O roteiro MCASP/IPC-03 do encerramento já está confirmado; o rito ainda
 * depende do contrato de Restos a Pagar (RAZ-207) antes de gerar fatos no razão.
 */
public final class EncerramentoExercicioPendenteRestosAPagarException extends RuntimeException implements ErroContrato {

    public EncerramentoExercicioPendenteRestosAPagarException(int exercicio) {
        super("encerramento do exercício %d pendente do contrato de restos a pagar".formatted(exercicio));
    }

    @Override
    public String codigo() {
        return "encerramento_exercicio_pendente_restos_a_pagar";
    }
}
