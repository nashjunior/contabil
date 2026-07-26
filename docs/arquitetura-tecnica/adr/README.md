# ADRs — Architecture Decision Records

[← Arquitetura técnica](../README.md) · [Índice geral](../../README.md)

> Cada decisão de arquitetura é **versionada** num arquivo próprio, com **Status** que evolui (Proposta → Aceita → Substituída/Depreciada). Uma decisão nunca se apaga: quando muda, cria-se um novo ADR que **supersede** o anterior (mesmo princípio de imutabilidade do produto). Formato: MADR enxuto.

| ADR | Decisão | Status |
| --- | --- | --- |
| [0001](./0001-base-unica-postgresql.md) | Base contábil única em PostgreSQL relacional | Aceita |
| [0002](./0002-monolito-modular.md) | Monólito modular (não microserviços no MVP) | Aceita |
| [0003](./0003-multi-tenant-rls.md) | Multi-tenant por `tenant_id` + RLS deny-by-default | Aceita |
| [0004](./0004-outbox-idempotente.md) | Publicação via outbox transacional + broker idempotente | Aceita |
| [0005](./0005-trilha-append-only-hash-chain.md) | Trilha append-only com hash-chain em store segregado | Aceita |
| [0006](./0006-dinheiro-decimal.md) | Dinheiro em `NUMERIC`/`BigDecimal` (nunca float) | Aceita |
| [0007](./0007-read-models-cqrs.md) | Read models / CQRS-lite para transparência e relatórios | Aceita |
| [0008](./0008-assinatura-provedor.md) | Assinatura via abstração de provedor (gov.br → ICP-Brasil) | Aceita |
| [0009](./0009-documentos-object-store.md) | Documentos assinados em object store/GED (não BLOB no banco) | Aceita |
| [0010](./0010-single-writer-failover.md) | Single-writer (Postgres primary) com failover fencing | Aceita |
| [0011](./0011-idempotencia-ponta-a-ponta.md) | Idempotência ponta a ponta | Aceita |
| [0012](./0012-stack-jvm.md) | Stack primária = **JVM (Java/Kotlin, Spring Boot)** | Aceita |
| [0013](./0013-persistencia-lote-fail-soft.md) | Persistência em lote com fail-soft e agregação de erros (`toInsert`/`toUpdate`/`toDelete`/`errors`) | Aceita |
| [0014](./0014-contratos-plataforma-ports.md) | Contratos dos serviços de plataforma como ports no `plataforma-domain` (taxonomia de erros estável) | Aceita |
| [0015](./0015-atribuicao-tenant-explicita-no-contrato.md) | Atribuição de tenant explícita no contrato de domínio; campo é `ente` (não `tenantId`) | Aceita |
| [0016](./0016-controle-acesso-mfa-movimentacao-recurso.md) | `ControleAcesso` na application: RBAC + MFA obrigatório para ações que movimentam recurso | Aceita |
| [0017](./0017-bff-oauth-assinatura-govbr.md) | BFF OAuth2 do signatário para Assinatura gov.br | Aceita |
| [0018](./0018-object-store-s3-compativel.md) | Object store S3-compatível (AWS SDK v2 / MinIO), cifrado, referência por URI | Aceita |
| [0020](./0020-f0-tls-backup-imutavel-restauracao.md) | F0: TLS em todas as interfaces, backup cifrado imutável e teste de restauração | Aceita |
| [0021](./0021-contabilizacao-execucao-despesa.md) | Contabilização da execução da despesa: um fato por evento, roteiro no produtor (`execucao`) | Aceita |

> **Nota:** o número **0019 não foi utilizado** — reservado durante execução paralela de agentes e nunca materializado em arquivo. Não renumerar os ADRs existentes (a numeração é histórica/imutável, mesmo princípio do ADR); o próximo ADR novo continua a partir do 0022.

## Como adicionar/mudar uma decisão

1. Novo ADR = próximo número, Status **Proposta**.
2. Ao ratificar, muda para **Aceita** (com data).
3. Ao rever, cria-se um **novo** ADR (Status Aceita) que aponta "Supersede ADR-NNNN"; o antigo vira **Substituída** com link para o sucessor. Não se edita a decisão original — versiona-se.

---

[← Arquitetura técnica](../README.md) · [Índice geral](../../README.md)
