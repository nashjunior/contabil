package br.contabil.assinatura;

interface ClienteTokenAssinaturaGovBr {

    AssinaturaGovBrOAuthToken trocarCodigoPorToken(String code, String codeVerifier);
}
