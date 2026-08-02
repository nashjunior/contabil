package br.contabil.razao.application;

import java.util.List;

import br.contabil.plataforma.domain.TenantId;

/** Resolve a configuração de contas para a transposição da DDR por fonte na abertura do ente informado. */
@FunctionalInterface
public interface ParametrosTransposicaoDdrAbertura {

    List<ParametroTransposicaoDdrAbertura> para(TenantId enteId);
}
