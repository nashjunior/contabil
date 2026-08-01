package br.contabil.razao.domain;

import br.contabil.plataforma.domain.ErroContrato;
import br.contabil.plataforma.domain.TenantId;

/** Não existe {@code periodo_contabil} para a competência informada, no ente (RAZ-206). */
public final class PeriodoContabilNaoEncontradoException extends RuntimeException implements ErroContrato {

    public PeriodoContabilNaoEncontradoException(TenantId enteId, int exercicio, int mes) {
        super("período contábil %d/%d não encontrado para o ente %s".formatted(mes, exercicio, enteId.valor()));
    }

    @Override
    public String codigo() {
        return "periodo_contabil_nao_encontrado";
    }
}
