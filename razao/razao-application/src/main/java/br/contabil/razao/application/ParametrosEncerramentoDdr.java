package br.contabil.razao.application;

import java.util.List;

import br.contabil.plataforma.domain.TenantId;

/** Resolve a configuração de contas para o encerramento da DDR por fonte no ente informado. */
@FunctionalInterface
public interface ParametrosEncerramentoDdr {

    List<ParametroEncerramentoDdr> para(TenantId enteId);
}
