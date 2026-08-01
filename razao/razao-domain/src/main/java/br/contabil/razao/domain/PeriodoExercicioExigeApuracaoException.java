package br.contabil.razao.domain;

import br.contabil.plataforma.domain.ErroContrato;

/**
 * O mês 13 é o encerramento do exercício (razao-contabil-schema.md, coluna
 * {@code periodo_contabil.mes}) — exige a apuração de resultado
 * patrimonial/orçamentário por lançamentos de encerramento antes de
 * transicionar o período (docs/15-fechamento-contabil.md §Preciso, itens 2/3;
 * ADR-0045). {@code EncerrarPeriodo} recusa mês 13; esse rito é escopo de
 * {@code EncerrarExercicio}.
 */
public final class PeriodoExercicioExigeApuracaoException extends RuntimeException implements ErroContrato {

    public PeriodoExercicioExigeApuracaoException(PeriodoContabilId id) {
        super(
                ("período %s é o mês 13 (encerramento do exercício) — exige apuração de resultado "
                                + "patrimonial/orçamentário antes de encerrar; use EncerrarExercicio, não o "
                                + "encerramento simples de período")
                        .formatted(id.valor()));
    }

    @Override
    public String codigo() {
        return "periodo_exercicio_exige_apuracao";
    }
}
