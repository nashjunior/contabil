package br.contabil.razao.domain;

import java.time.LocalDate;

import br.contabil.plataforma.domain.TenantId;

/**
 * Regra 5 (05-regras-de-negocio.md): período contábil encerrado rejeita novo
 * registro — correção só por estorno num período aberto. Levantada pelo use
 * case ao consultar o {@code PeriodoContabilPort}, antes de obter numero_seq
 * ou tocar o repositório do fato.
 */
public class PeriodoEncerradoException extends RuntimeException {

    public PeriodoEncerradoException(TenantId enteId, LocalDate dataCompetencia) {
        super("Período contábil encerrado (ou inexistente) para ente %s na competência %s: registro vedado"
                .formatted(enteId, dataCompetencia));
    }
}
