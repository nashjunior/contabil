package br.contabil.assinatura;

import br.contabil.plataforma.domain.ErroContrato;

/**
 * Erro {@code oauth_provedor_indisponivel}: o provedor OAuth2 gov.br falhou
 * ou recusou a troca de code por token (RAZ-107 2º passo). Cai no
 * {@code ErroContratoExceptionHandler} global, que gera o correlationId e
 * loga a causa — o controller só traduz a falha de infraestrutura do cliente
 * HTTP para este tipo.
 */
public final class AssinaturaGovBrOAuthProvedorIndisponivelException extends RuntimeException implements ErroContrato {

    public AssinaturaGovBrOAuthProvedorIndisponivelException(Throwable causa) {
        super("Falha ao trocar code OAuth2 por token gov.br", causa);
    }

    @Override
    public String codigo() {
        return "oauth_provedor_indisponivel";
    }
}
