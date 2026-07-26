---
name: guardiao
description: Umbrella dos guardiões do SIAFIC — roteia para os 4 guardiões especializados (arquitetura, segurança, observabilidade, IaC) e aplica um checklist rápido das invariantes inegociáveis (dinheiro decimal, razão append-only Σ=Σ, RLS multi-tenant, anti-backdating, segredos no cofre, PII mascarada, outbox idempotente, assinatura via provedor) + convenções de doc. Use ao revisar mudanças no SIAFIC, antes de commit, ou "passa o guardião" / "/guardiao".
---

# Guardião SIAFIC (umbrella)

O guardião do SIAFIC é dividido em **4 guardiões especializados** (subagentes canônicos em `.claude/agents/`). Esta skill é a **porta única**: faz um passe rápido e roteia para o especializado quando o diff é denso.

## Roteamento — qual guardião para qual mudança

| Mudou… | Guardião (subagente) |
| --- | --- |
| Código de app (camadas, razão, ports, entities, dinheiro, tenant) | **`guardiao-arquitetura`** |
| PII, tenant/auth, mascaramento, segredo, trilha, LGPD | **`guardiao-seguranca`** |
| Log, métrica, borda externa, caminho de SLA | **`guardiao-observabilidade`** |
| Terraform/IaC (rede, KMS, segredo em infra, residência, backup) | **`guardiao-iac`** |

Para revisão profunda de código, **invoque o subagente** correspondente (Agent tool) — ele é o checklist canônico. Esta skill existe para o passe rápido e para agentes que não invocam subagentes.

## Passe rápido — invariantes inegociáveis (código)

- **Dinheiro em `BigDecimal`/`NUMERIC`** — nunca `float`/`double`. → `guardiao-arquitetura`
- **Razão append-only + Σdébito=Σcrédito** — sem `UPDATE`/`DELETE` de fato/lançamento consolidado; correção por estorno. → `guardiao-arquitetura`
- **RLS multi-tenant deny-by-default** (`ente_id` + `app.ente_id`). → `guardiao-arquitetura`/`guardiao-seguranca`
- **Anti-backdating** — timestamp do servidor; competência só no período aberto. → `guardiao-arquitetura`
- **Segredos só no cofre**; F0 aceita passthrough de ambiente/secret file ([ADR-0024](../../../docs/arquitetura-tecnica/adr/0024-cofre-segredos-f0-env-passthrough.md)); nada hardcoded. → `guardiao-seguranca`/`guardiao-iac`
- **PII mascarada na fronteira pública** (CPF `***.456.***-**`; sem RG/endereço/banco). → `guardiao-seguranca`
- **Efeito externo via outbox idempotente** — sem chamada externa síncrona na transação. → `guardiao-arquitetura`
- **Assinatura via abstração de provedor** — nenhuma cripto caseira. → `guardiao-seguranca`

## Checklist de documentos (fase atual)

- H1 na 1ª linha; back-link abaixo; tabelas `| --- |`; Mermaid ASCII balanceado; sem link/âncora quebrado.
- Fases F0/F1/F2 coerentes com a tabela-mestre de [11-plataforma-transversal](../../../docs/11-plataforma-transversal.md) e o [07-roadmap](../../../docs/07-roadmap.md).
- Citações legais consistentes; na dúvida, "revalidar na fonte oficial". Para auditoria completa de consistência, use a skill **`auditar-docs`**.

As invariantes derivam de: [05-regras](../../../docs/05-regras-de-negocio.md), [11-plataforma](../../../docs/11-plataforma-transversal.md), [13-nfr](../../../docs/13-nfr-e-operacao.md), os [ADRs](../../../docs/arquitetura-tecnica/adr/) e o [schema do razão](../../../docs/arquitetura-tecnica/razao-contabil-schema.md).
