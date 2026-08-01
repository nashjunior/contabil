# CLAUDE.md

> **As convenções, o índice de agentes/skills e as regras deste projeto são canônicos em [AGENTS.md](./AGENTS.md).** Leia-o primeiro. Este arquivo só aponta para lá — fonte única, evita divergência.

Resumo de 30 segundos (o detalhe está no [AGENTS.md](./AGENTS.md)):

- Projeto: **SIAFIC** (Oberware) — sistema de execução orçamentário-financeiro-contábil (Decreto 10.540/2020). Fase: **F0 em finalização (~95%)** — núcleo (razão/IAM/auditoria/mascaramento/assinatura/piso de segurança) entregue e credencial de assinatura gov.br provisionada (RAZ-39/RAZ-31 concluídas); pendente apenas o smoke test e2e contra o gov.br staging real (operacional, fora do sandbox) e a evidência operacional em ente-piloto (RAZ-38, backlog). Acompanhamento por fase em [docs/14](docs/14-status-feito-pendente.md) ([docs/](docs/README.md)).
- Stack decidida: **JVM (Java/Kotlin), Spring Boot, PostgreSQL** — decisões versionadas em [ADRs](docs/arquitetura-tecnica/adr/).
- Invariantes inegociáveis: dinheiro em `BigDecimal`; razão append-only Σ=Σ atômico; RLS multi-tenant; segredo no cofre; PII mascarada; efeito externo via outbox; **persistência em lote com fail-soft** (ver [AGENTS.md](./AGENTS.md) e [ADR-0013](docs/arquitetura-tecnica/adr/0013-persistencia-lote-fail-soft.md)).
- Revisão: guardiões em [.claude/agents/](.claude/agents/); skills e workflow em [.claude/](.claude/). Antes de editar doc, `planejar-doc`; depois, `revisar-ddd`/`auditar-docs`/guardião.
