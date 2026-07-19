package br.contabil.razao.domain.repository;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.PeriodoEncerradoException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Consulta do período contábil correspondente a uma data de competência
 * (Regra 5). Implementações rejeitam com {@link PeriodoEncerradoException}
 * quando o período está encerrado ou não existe — nunca retornam nulo/booleano.
 */
public interface PeriodoContabilPort {

    UUID periodoAbertoPara(TenantId enteId, LocalDate dataCompetencia);
}
