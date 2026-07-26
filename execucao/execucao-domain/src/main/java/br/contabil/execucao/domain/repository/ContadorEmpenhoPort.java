package br.contabil.execucao.domain.repository;

import br.contabil.plataforma.domain.TenantId;

/**
 * Numeração sequencial gapless do empenho, por ente/exercício (mesmo padrão do
 * {@code ContadorFatoPort} do razão: a implementação obtém o próximo número via
 * lock de linha na mesma transação do insert do empenho).
 */
public interface ContadorEmpenhoPort {

    long proximoNumero(TenantId enteId, int exercicio);
}
