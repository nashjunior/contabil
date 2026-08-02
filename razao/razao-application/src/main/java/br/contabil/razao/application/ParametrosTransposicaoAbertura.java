package br.contabil.razao.application;

import java.util.List;

import br.contabil.plataforma.domain.TenantId;

/**
 * Resolve os pares de transposição de saldo patrimonial na abertura do exercício
 * seguinte no ente informado (RAZ-270, mesmo padrão do RAZ-260/RAZ-266) —
 * {@code conta_pcasp} é tenant-scoped.
 */
@FunctionalInterface
public interface ParametrosTransposicaoAbertura {
    List<ParametroTransposicaoAbertura> para(TenantId enteId);
}
