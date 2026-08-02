package br.contabil.razao.application;

import java.util.List;

import br.contabil.plataforma.domain.TenantId;

/**
 * Resolve os pares de encerramento das contas de controle orçamentário PCASP (classes
 * 5/6) no ente informado (RAZ-270, mesmo padrão do RAZ-260/RAZ-266) —
 * {@code conta_pcasp} é tenant-scoped.
 */
@FunctionalInterface
public interface ParametrosEncerramentoOrcamentario {
    List<ParametroEncerramentoOrcamentario> para(TenantId enteId);
}
