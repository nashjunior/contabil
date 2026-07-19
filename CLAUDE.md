# CLAUDE.md

> **As convenções, o índice de agentes/skills e as regras deste projeto são canônicos em [AGENTS.md](./AGENTS.md).** Leia-o primeiro. Este arquivo só aponta para lá — fonte única, evita divergência.

Resumo de 30 segundos (o detalhe está no [AGENTS.md](./AGENTS.md)):

- Projeto: **SIAFIC** (Oberware) — sistema de execução orçamentário-financeiro-contábil (Decreto 10.540/2020). Fase: **F0 em finalização** — núcleo (razão/IAM/auditoria/mascaramento/assinatura/piso de segurança) entregue; pendente e2e de assinatura (RAZ-39, bloqueio externo) e evidência operacional em ente-piloto (RAZ-38) ([docs/](docs/README.md)).
- Stack decidida: **JVM (Java/Kotlin), Spring Boot, PostgreSQL** — decisões versionadas em [ADRs](docs/arquitetura-tecnica/adr/).
- Invariantes inegociáveis: dinheiro em `BigDecimal`; razão append-only Σ=Σ atômico; RLS multi-tenant; segredo no cofre; PII mascarada; efeito externo via outbox; **persistência em lote com fail-soft** (ver [AGENTS.md](./AGENTS.md) e [ADR-0013](docs/arquitetura-tecnica/adr/0013-persistencia-lote-fail-soft.md)).
- Revisão: guardiões em [.claude/agents/](.claude/agents/); skills e workflow em [.claude/](.claude/). Antes de editar doc, `planejar-doc`; depois, `revisar-ddd`/`auditar-docs`/guardião.
