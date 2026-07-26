# RAZ-126 — Runbook: registro do cliente OIDC de login gov.br

Provisionamento operacional exigido para ligar o **BFF de login geral** — o fluxo
OIDC `authorization_code`+PKCE que obtém a asserção gov.br apresentada às APIs de
execução/consulta (`Authorization: Bearer <asserção gov.br>`, RAZ-101/RAZ-105).
Autoridade de domínio: [ADR-0035](../arquitetura-tecnica/adr/0035-bff-login-oidc-govbr.md)
(BFF de login OIDC gov.br). Base legal: LGPD (Lei 13.709/2018, dado pessoal e
tratamento no login), Lei 14.063/2020 (identidade digital gov.br). Referência de
provisionamento análoga (escopo de **assinatura**, não login):
[RAZ-24 — âncoras de confiança ICP-Brasil](RAZ-24-runbook-icp-brasil-trust-anchors.md)
e [ADR-0017](../arquitetura-tecnica/adr/0017-bff-oauth-assinatura-govbr.md).

## Por que isto não é código

O BFF de login precisa de um **cliente OAuth2/OIDC registrado no gov.br** para o
escopo de autenticação (`openid profile`) — com `client_id`, `client_secret` e a
`redirect_uri` de callback autorizada. Esse registro **não é gerado por código**: é
um ato administrativo na conta gov.br do ente (Login Único / provedor de identidade
do governo), com credenciais de governo, exatamente como o registro do cliente de
**assinatura** documentado no RAZ-39/RAZ-31 — porém para um **escopo diferente**
(login geral, não `sign`/`signature_session`). Um `client_id` de assinatura **não
serve** para login; são clientes e escopos distintos.

## Bloqueador externo (mesma natureza do RAZ-39)

O registro exige **acesso administrativo humano à conta gov.br do ente**. Enquanto
não houver cliente OIDC de login registrado (staging e, depois, produção), o e2e
real do login fica bloqueado. A mecânica do BFF (redirect → callback → troca de
token → `ServicoIdentidade.autenticar` → cookie de sessão) é **testável com stub
local**, sem o registro real — como já se faz no fluxo de assinatura (ADR-0017).

- **Dono do desbloqueio:** administrador da conta gov.br do ente (RAZ-39 tem a mesma
  dependência para assinatura).
- **Ação de desbloqueio:** registrar o cliente OIDC de login e provisionar
  `client_id`/`client_secret`/`redirect_uri` no cofre/ambiente (ADR-0024).

## O que provisionar

1. Registrar a aplicação no provedor de identidade gov.br (Login Único) para o
   escopo de **autenticação** (`openid profile`), obtendo `client_id` e
   `client_secret`. **Staging e produção são clientes distintos** — cada ambiente
   tem seu par de credenciais e sua `redirect_uri`.
2. Autorizar a `redirect_uri` de callback do BFF (`.../sessao/oauth/callback`) —
   deve ser **`https`** (o `client_secret`/`code`/`access_token` trafegam nela);
   exceção só para loopback em desenvolvimento local.
3. Confirmar o nível de garantia exigido (selo gov.br Prata/Ouro) para que
   `mfaConcluido` valha nas ações que movimentam recurso (ADR-0016). Login simples
   (LER) não exige MFA; movimentação exige.
4. Injetar as credenciais via cofre/ambiente (ADR-0024) sob o prefixo de
   configuração do BFF de login. Sem elas, o endpoint de login **falha fechado** no
   início do fluxo, preservando o startup local.

## Convenções de segurança (fail-closed)

- A asserção gov.br é guardada **server-side**; ao navegador só vai um cookie
  `HttpOnly`+`Secure`+`SameSite=Lax`. A asserção **nunca** aparece em armazenamento
  alcançável por JS (LGPD/XSS).
- `state` server-side protege a perna OAuth (CSRF); `code_verifier` nunca sai da
  sessão. O `ente` da sessão vem da **concessão RBAC verificada** (deny-by-default),
  nunca de cookie/header cru; a seleção entre múltiplos entes de um mesmo CPF
  (ADR-0030) é resolvida pela asserção/concessão, não por entrada do navegador — se a
  asserção não resolver o ente e houver múltiplas concessões, o login **falha fechado**
  (`SemPermissaoException`), e a escolha de ente é ponto de desenho da impl (RAZ-126 [BE]).
- A API reverifica a asserção **a cada requisição** via `ServicoIdentidade` mesmo no
  caminho por cookie — sem cache de decisão de identidade/expiração/MFA.

---

[← Operação](./README.md)
