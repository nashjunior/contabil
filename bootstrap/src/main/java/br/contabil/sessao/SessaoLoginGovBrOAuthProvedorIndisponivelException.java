package br.contabil.sessao;

import br.contabil.plataforma.domain.ErroContrato;

/**
 * Erro {@code login_oauth_provedor_indisponivel}: o provedor OIDC gov.br falhou ou
 * recusou a troca de code por token no BFF de <b>login</b> (ADR-0035; distinto de
 * {@code oauth_provedor_indisponivel} do BFF de assinatura, ADR-0017). Cai no
 * {@code ErroContratoExceptionHandler} global, que gera o correlationId e loga a
 * causa — o controller só traduz a falha de infraestrutura do cliente HTTP.
 */
public final class SessaoLoginGovBrOAuthProvedorIndisponivelException extends RuntimeException implements ErroContrato {

    public SessaoLoginGovBrOAuthProvedorIndisponivelException(Throwable causa) {
        super("Falha ao trocar code OIDC por token gov.br de login", causa);
    }

    @Override
    public String codigo() {
        return "login_oauth_provedor_indisponivel";
    }
}
