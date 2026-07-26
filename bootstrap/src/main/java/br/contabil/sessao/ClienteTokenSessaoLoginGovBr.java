package br.contabil.sessao;

interface ClienteTokenSessaoLoginGovBr {

    SessaoLoginGovBrOAuthToken trocarCodigoPorToken(String code, String codeVerifier);
}
