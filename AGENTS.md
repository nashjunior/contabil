# AGENTS.md — SIAFIC (Oberware)

Fonte **canônica** das convenções e do ferramental de IA deste projeto. `CLAUDE.md` aponta para cá — ao mudar uma convenção, mude **só aqui** (evita drift).

## O que é

SIAFIC — Sistema Único e Integrado de Execução Orçamentária, Administração Financeira e Controle (Decreto 10.540/2020), para estados e municípios. **Núcleo** contábil-orçamentário-financeiro + transparência + saída SICONFI; **licitações/patrimônio/folha/arrecadação são estruturantes** (integram por fora). Fase atual: **F0 em finalização** — motor de razão, IAM/RBAC, trilha de auditoria, mascaramento, assinatura eletrônica (lógica) e piso de segurança (TLS/backup/cofre) entregues; pendente: integração e2e do OAuth2 gov.br (bloqueada em provisionamento externo humano, RAZ-39) e evidência operacional em ente-piloto (RAZ-38). Índice: [docs/README.md](docs/README.md).

## Convenções de código (JVM)

Cada regra tem sua autoridade num ADR; o [`guardiao-arquitetura`](.claude/agents/guardiao-arquitetura.md) as fiscaliza.

- **Stack:** JVM (Java/Kotlin), Spring Boot — [ADR-0012](docs/arquitetura-tecnica/adr/0012-stack-jvm.md). Domínio nomeado em **pt-BR** (`Empenho`, `Liquidacao`, `FatoContabil`).
- **Dinheiro:** `BigDecimal` / `NUMERIC(18,2)`, nunca `float` — [ADR-0006](docs/arquitetura-tecnica/adr/0006-dinheiro-decimal.md).
- **Camadas:** monólito modular `domain/application/infra`, dependências para dentro; **interfaces de repository/persistência no `domain`**; use cases (`application`) são **POJOs sem anotação de framework** — a **`infra` faz o wiring (`@Configuration`/`@Bean`) e detém a transação** (borda); adapters na `infra` — [ADR-0002](docs/arquitetura-tecnica/adr/0002-monolito-modular.md).
- **Razão:** append-only (correção por estorno), Σdébito=Σcrédito, **atômico** — [razao-schema](docs/arquitetura-tecnica/razao-contabil-schema.md).
- **Multi-ente:** `ente_id` + RLS deny-by-default — [ADR-0003](docs/arquitetura-tecnica/adr/0003-multi-tenant-rls.md).
- **Efeito externo:** via outbox idempotente, nunca síncrono na transação — [ADR-0004](docs/arquitetura-tecnica/adr/0004-outbox-idempotente.md) / [ADR-0011](docs/arquitetura-tecnica/adr/0011-idempotencia-ponta-a-ponta.md).
- **Persistência em lote (fail-soft):** ver abaixo — [ADR-0013](docs/arquitetura-tecnica/adr/0013-persistencia-lote-fail-soft.md).
- **Segurança/LGPD:** segredo só no cofre; PII mascarada na fronteira; tenant de claim verificado — [transversais/04-lgpd](docs/transversais/04-lgpd.md), [13-nfr](docs/13-nfr-e-operacao.md).

### Persistência em lote com fail-soft (canônico: ADR-0013)

Operação sobre **coleção** de registros (ingestão de estruturantes, migração, read-models, publicação):

- **Preferir batch** insert/update (JDBC batch, `INSERT … ON CONFLICT`), não linha-a-linha.
- **Particionar** a entrada em `toInsert` / `toUpdate` / `toDelete`.
- **Fail-soft:** um item ruim **não derruba o lote** — agrega a falha em `errors` (item + motivo) e **retorna quais não puderam** ser inseridos/atualizados.
- **Unidade de atomicidade = o fato/registro**; o lote é fail-soft **entre** unidades, cada unidade é atômica.
- **Fronteira (importante):** vale para a **borda** (ingestão/migração/read-models/publicação). **NÃO** para a transação do **razão** — um fato é all-or-nothing (Σ=Σ); fail-soft parcial de partidas dobradas é proibido.
- Os rejeitados reprocessam com segurança (idempotência, DLQ) e aparecem na trilha/relatório ([fluxo 5](docs/04-fluxos.md#5-integração-com-sistemas-estruturantes), [migração](docs/12-migracao.md)).

## Guardiões (subagentes — checklists canônicos)

Só reportam, não editam. Cada um é a fonte única das suas regras.

| Guardião | Fiscaliza |
| --- | --- |
| [`guardiao-arquitetura`](.claude/agents/guardiao-arquitetura.md) | Camadas, razão, dinheiro, tenant, outbox, **persistência em lote** |
| [`guardiao-seguranca`](.claude/agents/guardiao-seguranca.md) | LGPD, PII/mascaramento, tenant de claim, segredo, trilha |
| [`guardiao-observabilidade`](.claude/agents/guardiao-observabilidade.md) | Log/correlação, métrica de SLA, circuit breaker (antecipatório) |
| [`guardiao-iac`](.claude/agents/guardiao-iac.md) | Rede privada, KMS, segredo em infra, residência, backup (antecipatório) |

## Skills e workflow

- **Planejar/editar:** `planejar-doc` (plano antes de editar) · `nova-spec` (nova spec no formato da casa) · `adr` (registrar decisão versionada).
- **Revisar:** `guardiao` (umbrella → roteia p/ os 4) · `revisar-ddd` (modelo) · `auditar-docs` (consistência mecânica).
- **Fonte:** `pesquisar-fonte` (fonte oficial p/ fechar "revalidar na fonte") · `/deep-research` (amplo).
- **Workflow:** `revisao-multilente` (auditoria adversarial de 4 lentes, report-only).

## Fluxo de trabalho (docs)

`planejar-doc` → editar (`nova-spec`/manual) → revisar (`revisar-ddd` / `auditar-docs` / guardião) → registrar decisão (`adr`). Citações legais não confirmadas ficam **"revalidar na fonte oficial"** → fechar com `pesquisar-fonte`.
