package br.contabil;

import br.contabil.plataforma.domain.TenantId;

/**
 * A conta do PCASP referenciada pelo roteiro de contabilização (ADR-0021) não
 * está cadastrada em {@code conta_pcasp} para o ente — falha de configuração
 * do plano de contas, não uma regra de negócio da execução.
 */
public class ContaPcaspNaoEncontradaException extends RuntimeException {

    public ContaPcaspNaoEncontradaException(TenantId enteId, String codigoPcasp) {
        super("Conta PCASP %s não encontrada para o ente %s".formatted(codigoPcasp, enteId));
    }
}
