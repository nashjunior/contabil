package br.contabil.razao.domain;

import br.contabil.plataforma.domain.ErroContrato;
import br.contabil.plataforma.domain.TenantId;

/**
 * A conta consultada não existe em {@code conta_pcasp} para o ente (RAZ-117 /
 * ADR-0030 §6, gap 2). Distingue "conta inexistente/errada" — erro do cliente,
 * HTTP 404 — de "conta existente sem lançamento", que é saldo zero legítimo.
 *
 * <p>Implementa {@link ErroContrato}: o {@code codigo} {@code conta_nao_encontrada}
 * é estável e parte do contrato (§6.1). O mapeamento HTTP (404 + envelope
 * {@code {codigo,mensagem,detalhes}}) vive na borda ({@code ErroContratoExceptionHandler}),
 * convergido com a RAZ-116. Camada PURA — sem Spring.
 */
public class ContaNaoEncontradaException extends RuntimeException implements ErroContrato {

    private static final String CODIGO = "conta_nao_encontrada";

    public ContaNaoEncontradaException(TenantId enteId, ContaContabilId contaId) {
        super("Conta %s não encontrada no ente %s".formatted(contaId.valor(), enteId.valor()));
    }

    @Override
    public String codigo() {
        return CODIGO;
    }
}
