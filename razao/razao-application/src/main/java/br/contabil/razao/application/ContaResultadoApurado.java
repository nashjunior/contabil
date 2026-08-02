package br.contabil.razao.application;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.razao.domain.ContaContabilId;

/**
 * Resolve a conta de Patrimônio Líquido "Superávits ou Déficits do Exercício" para a
 * apuração do resultado patrimonial no ente informado (RAZ-270, mesmo padrão do
 * RAZ-260/RAZ-266) — {@code conta_pcasp} é tenant-scoped.
 */
@FunctionalInterface
public interface ContaResultadoApurado {
    ContaContabilId para(TenantId enteId);
}
