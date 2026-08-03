# Status de entrega — feito × pendente

[← Índice](./README.md)

> **Este documento é um espelho de acompanhamento, não fonte de verdade.** As
> fases globais F0–F4 são definidas em [07 · Roadmap](./07-roadmap.md); a maturidade
> por serviço transversal (M0/M1/M2) e seu mapeamento às fases é a **tabela-mestre**
> do [11 · Plataforma e transversais](./11-plataforma-transversal.md). Em caso de
> divergência, **07 e 11 prevalecem** — atualize-os primeiro e reflita aqui. O estado
> do topo (fase atual) é resumido em [AGENTS.md](../AGENTS.md); as decisões, nos
> [ADRs](./arquitetura-tecnica/adr/).

**Legenda:** ✅ entregue · 🟡 parcial / em curso · ⏳ pendente · 🔒 bloqueador externo (fora do código).

**Fase atual:** **F0 em finalização (~95%)** — núcleo entregue e a credencial de
assinatura gov.br provisionada (RAZ-39/RAZ-31 concluídas). Restam apenas dois itens
**operacionais** (não de código): o smoke test e2e contra o gov.br staging real e a
evidência de operação em ente-piloto (RAZ-38).

## F0 — Fundações

| Item | Estado | Evidência / autoridade |
| --- | --- | --- |
| Base única + motor de razão (partidas dobradas, PCASP, append-only Σ=Σ) | ✅ | [razão-schema](./arquitetura-tecnica/razao-contabil-schema.md), [ADR-0006](./arquitetura-tecnica/adr/0006-dinheiro-decimal.md) |
| Contratos/ports dos serviços de plataforma | ✅ | [ADR-0014](./arquitetura-tecnica/adr/0014-contratos-plataforma-ports.md) |
| Identidade / RBAC (CPF/certificado gov.br) | ✅ | `ServicoIdentidadeGovBrIcp` |
| Trilha de auditoria imutável (hash-chain) | ✅ | Fluxo [7 · Trilha de auditoria](./04-fluxos.md#7-trilha-de-auditoria-e-vedações) |
| Mascaramento de PII na fronteira | ✅ | [transversais/04 · LGPD](./transversais/04-lgpd.md) |
| Assinatura gov.br avançada (lógica) + BFF OAuth2 de assinatura | ✅ | [ADR-0008](./arquitetura-tecnica/adr/0008-assinatura-provedor.md) / [ADR-0017](./arquitetura-tecnica/adr/0017-bff-oauth-assinatura-govbr.md) |
| Entrega garantida / outbox idempotente (interface + impl mínima) | ✅ | [ADR-0004](./arquitetura-tecnica/adr/0004-outbox-idempotente.md) |
| Motor de publicação mínimo (transparência, 1º consumidor) | 🟡 | Fluxo [9 · Transparência](./04-fluxos.md#9-transparência-em-tempo-real) |
| Piso de segurança F0 (TLS, backup imutável, restauração, cofre passthrough) | ✅ | [ADR-0020](./arquitetura-tecnica/adr/0020-f0-tls-backup-imutavel-restauracao.md), [F0-runbook](./operacao/F0-runbook-tls-backup-restauracao.md) |
| Provisionamento âncoras ICP-Brasil (operacional) | 🟡 | [RAZ-24 runbook](./operacao/RAZ-24-runbook-icp-brasil-trust-anchors.md) |
| Credencial OAuth2 gov.br de assinatura (staging) — RAZ-39 | ✅ | Provisão atestada pelo owner; ver [RAZ-24 runbook](./operacao/RAZ-24-runbook-icp-brasil-trust-anchors.md) [†] |
| **Smoke test e2e de assinatura contra gov.br staging real** | ⏳ 🔒 | operacional — não executável do sandbox; roda no ambiente do ente |
| **Evidência operacional em ente-piloto (TLS/backup WORM/1ª restauração)** | ⏳ | **RAZ-38** (backlog) |

> [†] **RAZ-39 reconciliada (2026-08-01):** a issue [OPS] de provisionar a credencial
> gov.br está **`done`** no rastreador (provisão atestada pelo owner). O *e2e de
> assinatura* propriamente dito nunca foi escopo da RAZ-39 — pertence à RAZ-31 (BFF,
> também concluída). Resta apenas o smoke test operacional contra o staging real.
> Item de auditoria em aberto: anexar ao dossiê os dados **não sensíveis** (`client_id`
> de staging, redirect URI efetiva, resultado do smoke test 302→200) para evidência
> TCE/ANPD; o `client_secret` permanece só no cofre.

## F1 — MVP de conformidade + prestação de contas (go-live)

| Item | Estado | Autoridade |
| --- | --- | --- |
| Execução da despesa (empenho→liquidação→pagamento) | 🟡 | [Fluxo do operador + contrato de API](./arquitetura-tecnica/fluxo-execucao-operador-contrato-api.md), [ADR-0021](./arquitetura-tecnica/adr/0021-contabilizacao-execucao-despesa.md)/[ADR-0023](./arquitetura-tecnica/adr/0023-gate-aprovacao-pagamento-segregacao.md) |
| Restos a pagar + trava LRF art. 42 `[OBRIGATÓRIO]` | ⏳ | [11 · Plataforma e transversais](./11-plataforma-transversal.md) |
| Carga da LOA + créditos adicionais | ⏳ | [11 · Plataforma e transversais](./11-plataforma-transversal.md) (CF art. 167) |
| Fechamento de período / apuração do resultado | ⏳ | Fluxo [8 · Fechamento de período](./04-fluxos.md#8-fechamento-de-período) |
| Transparência ativa + dados abertos (CSV/JSON) `[OBRIGATÓRIO]` | 🟡 | [transversais/03 · Transparência](./transversais/03-transparencia.md) |
| Prestação de contas: MSC (Portaria 642) + remessa TCE + RREO/RGF/DCA | ⏳ | Fluxo [10 · Consolidação nacional](./04-fluxos.md#10-consolidação-nacional-siconfi) — bloqueante de controle externo |
| Assinatura qualificada ICP-Brasil (M1) | 🟡 | [transversais/01 · Assinatura](./transversais/01-assinatura-eletronica.md) — caminho qualificado via escopo `icp_brasil` e multiassinatura incremental implementados (RAZ-208); **conformidade VALIDAR/ITI definida como evidência operacional** ([ADR-0058](./arquitetura-tecnica/adr/0058-conformidade-validar-iti-evidencia-operacional.md) + [runbook RAZ-249](./operacao/RAZ-249-runbook-evidencia-validar-iti.md), 1ª evidência com ENTE-PILOTO/OWNER); faltam carimbo ACT e PAdES-LTV |
| Login OIDC gov.br (asserção Bearer) | 🟡 | [ADR-0035](./arquitetura-tecnica/adr/0035-bff-login-oidc-govbr.md) — backend entregue (RAZ-128); frontend ainda usa login de dev |
| Governança LGPD (base legal, retenção, direitos) — M1 | ⏳ | [transversais/04 · LGPD](./transversais/04-lgpd.md) |
| Acessibilidade eMAG no back-office (não só portal) | ⏳ | [transversais/05 · Acessibilidade](./transversais/05-acessibilidade.md) |
| Migração/implantação do ente-piloto | ⏳ | [12 · Migração](./12-migracao.md) — **bloqueante de go-live** |

## F2–F4 — futuro

| Fase | Escopo | Estado |
| --- | --- | --- |
| **F2** — Integração e consolidação | Conectores estruturantes; gate PNCP bloqueante (art. 94); conciliação bancária; SICONFI ampliado; remuneração individualizada | ⏳ |
| **F3** — Valor e inteligência | Painéis, relatórios de exceção, alertas; dados abertos avançados; EBT 360 (CGU) | ⏳ |
| **F4** — Escala e evolução | Elaboração PPA/LDO/LOA, BI, multi-ente em escala | ⏳ |

## Estado dos documentos de produto

Espelho da tabela em [README · Documentos do produto](./README.md#documentos-do-produto):

| Documento | Estado |
| --- | --- |
| README, PRD | ✅ |
| [Modelo de dados](./10-modelo-dados.md) | 🟡 parcial (ciclo da despesa) |
| Máquinas de estado (empenho/período) | 🟡 parcial (empenho) |
| Matriz de perfis (RBAC) | ✅ código F0 |
| User stories + critérios de aceite (Gherkin) | ⏳ pendente |
| [Fluxo operador + contrato de API — execução (F1)](./arquitetura-tecnica/fluxo-execucao-operador-contrato-api.md) | ✅ ratificado (RAZ-79) |
| [Design system SIAFIC (F1)](./arquitetura-tecnica/design-system-tokens-componentes.md) | ✅ ratificado (RAZ-100) |

---

[← 13 · NFR e operação](./13-nfr-e-operacao.md) · [Índice](./README.md)
