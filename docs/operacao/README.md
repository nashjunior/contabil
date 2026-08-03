# Operação — piso de segurança F0

Documentação **operacional** do sistema contábil (SIAFIC), fora do domínio Java.
Autoridade das decisões: as [ADRs](../arquitetura-tecnica/adr/). Os artefatos
executáveis vivem em [`infra/`](../../infra/) na raiz.

## Índice

| Documento | Conteúdo |
| --- | --- |
| [F0 — TLS, backup imutável e restauração](F0-runbook-tls-backup-restauracao.md) | Runbook e **evidência de auditoria** TCE/ANPD do piso F0 (RAZ-7/RAZ-36) |
| [RAZ-24 — âncoras de confiança ICP-Brasil](RAZ-24-runbook-icp-brasil-trust-anchors.md) | Provisionamento do bundle de CA para verificação de revogação na assinatura eletrônica |
| [RAZ-126 — registro do cliente OIDC de login gov.br](RAZ-126-runbook-oidc-login-govbr.md) | Registro do cliente OAuth2/OIDC de **login geral** no gov.br (escopo `openid`, distinto do de assinatura) — bloqueador externo do e2e de login |
| [RAZ-249 — evidência de conformidade VALIDAR/ITI](RAZ-249-runbook-evidencia-validar-iti.md) | Procedimento de **evidência operacional** da assinatura qualificada: relatório do `validar.iti.gov.br` anexado ao dossiê do documento — owner ENTE-PILOTO/OWNER |

## Decisões relacionadas

- [ADR-0020 — F0: TLS, backup cifrado imutável e teste de restauração](../arquitetura-tecnica/adr/0020-f0-tls-backup-imutavel-restauracao.md)
- [ADR-0008 — Assinatura eletrônica: abstração de provedor](../arquitetura-tecnica/adr/0008-assinatura-provedor.md)
- [ADR-0017 — BFF OAuth2 do signatário para assinatura gov.br](../arquitetura-tecnica/adr/0017-bff-oauth-assinatura-govbr.md)
- [ADR-0035 — BFF de login OIDC gov.br (autenticação geral, não assinatura)](../arquitetura-tecnica/adr/0035-bff-login-oidc-govbr.md)
- [ADR-0018 — Object store S3-compatível, cifrado, referência por URI](../arquitetura-tecnica/adr/0018-object-store-s3-compativel.md)
- [ADR-0058 — Conformidade VALIDAR/ITI é evidência operacional anexada ao dossiê do documento](../arquitetura-tecnica/adr/0058-conformidade-validar-iti-evidencia-operacional.md)
