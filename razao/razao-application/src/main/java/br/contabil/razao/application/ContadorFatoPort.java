package br.contabil.razao.application;

import br.contabil.plataforma.domain.TenantId;

/**
 * Numeração sequencial cronológica gapless do fato contábil. A implementação
 * de infra obtém o próximo número via lock de linha na mesma transação do
 * insert do fato ({@code UPDATE contador_fato ... RETURNING}) — se a transação
 * fizer rollback, o incremento desfaz junto (sem buraco).
 */
public interface ContadorFatoPort {

    long proximoNumeroSeq(TenantId enteId);
}
