package br.contabil.razao.domain;

import br.contabil.plataforma.domain.ErroContrato;

/**
 * O período já está {@code encerrado} (ADR-0045) — a transição condicional
 * {@code aberto -> encerrado} não vinga porque outra chamada já venceu antes
 * (corrida) ou porque o chamador tentou encerrar de novo. Conflito de estado,
 * não payload malformado (mesmo bucket de {@code liquidacao_ja_decidida}).
 *
 * <p>Nome deliberadamente distinto de {@link PeriodoEncerradoException}
 * (que rejeita um NOVO lançamento num período já fechado) — semânticas
 * diferentes, para não confundir em code review/stack trace.
 */
public final class EncerramentoConflitanteException extends RuntimeException implements ErroContrato {

    public EncerramentoConflitanteException(PeriodoContabilId id) {
        super("período contábil %s já está encerrado".formatted(id.valor()));
    }

    @Override
    public String codigo() {
        return "periodo_ja_encerrado";
    }
}
