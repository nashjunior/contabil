package br.contabil.razao.domain.repository;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.DemonstracoesDcasp;

/**
 * Read port das demonstrações DCASP (ADR-0047). A implementação consulta fatos
 * e lançamentos do razão por ente/exercício e devolve projeções reconstruíveis.
 */
public interface DemonstracoesDcaspPort {

    DemonstracoesDcasp demonstracoes(TenantId enteId, int exercicio);
}
