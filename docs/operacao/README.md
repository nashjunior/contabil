# Operação — piso de segurança F0

Documentação **operacional** do sistema contábil (SIAFIC), fora do domínio Java.
Autoridade das decisões: as [ADRs](../arquitetura-tecnica/adr/). Os artefatos
executáveis vivem em [`infra/`](../../infra/) na raiz.

## Índice

| Documento | Conteúdo |
| --- | --- |
| [F0 — TLS, backup imutável e restauração](F0-runbook-tls-backup-restauracao.md) | Runbook e **evidência de auditoria** TCE/ANPD do piso F0 (RAZ-7/RAZ-36) |
| [RAZ-24 — âncoras de confiança ICP-Brasil](RAZ-24-runbook-icp-brasil-trust-anchors.md) | Provisionamento do bundle de CA para verificação de revogação na assinatura eletrônica |

## Decisões relacionadas

- [ADR-0020 — F0: TLS, backup cifrado imutável e teste de restauração](../arquitetura-tecnica/adr/0020-f0-tls-backup-imutavel-restauracao.md)
- [ADR-0008 — Assinatura eletrônica: abstração de provedor](../arquitetura-tecnica/adr/0008-assinatura-provedor.md)
