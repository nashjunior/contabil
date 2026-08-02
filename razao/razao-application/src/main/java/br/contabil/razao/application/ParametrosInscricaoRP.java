package br.contabil.razao.application;

import java.util.List;

import br.contabil.plataforma.domain.TenantId;

/** Resolve a configuração de contas para inscrição de Restos a Pagar no ente informado. */
@FunctionalInterface
public interface ParametrosInscricaoRP {

    List<ParametroInscricaoRP> para(TenantId enteId);
}
