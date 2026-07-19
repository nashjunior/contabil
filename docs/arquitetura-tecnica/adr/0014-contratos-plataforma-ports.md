# ADR-0014 · Contratos dos serviços de plataforma como ports no `plataforma-domain`

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** O [doc 11 §Contratos](../../11-plataforma-transversal.md#contratosinterfaces-dos-serviços-de-plataforma) manda **definir a interface de todos os serviços transversais agora** (Identidade/RBAC, Assinatura, Auditoria, Publicação/Entrega, Mascaramento, Ingestão, Cofre), inclusive os que só serão implementados em fases posteriores, para os módulos dependerem de um **contrato estável** e não de um *retrofit*. Faltava materializar esses contratos em código, num único lugar estável.
- **Decisão:** Cada serviço transversal é um **port** (interface) no shared kernel **`plataforma-domain`** (`br.contabil.plataforma.domain.<contrato>`), POJO puro ([ADR-0002](./0002-monolito-modular.md)), consumido por todos os módulos. Convenções do contrato:
  - **Erros** são exceções de domínio (`RuntimeException`) que implementam `ErroContrato` e expõem um **código estável legível por máquina** (taxonomia do doc 11 §Contratos: `nao_autenticado`, `sem_permissao`, `certificado_invalido`, `sem_base_legal`, `origem_nao_confiavel`, `sem_escopo`, …). Mudar um código é *breaking change* e exige novo ADR.
  - **Auditoria** é segregada em dois ports: `AuditoriaEscrita` (append imutável, [ADR-0005](./0005-trilha-append-only-hash-chain.md)) e `AuditoriaLeitura` (consulta por read model, [ADR-0007](./0007-read-models-cqrs.md)).
  - **Idempotência ponta a ponta** é um único conceito reusado pela Ingestão (inbox) e pela Entrega (outbox): `ChaveIdempotencia` ([ADR-0011](./0011-idempotencia-ponta-a-ponta.md) / [ADR-0004](./0004-outbox-idempotente.md)). Entrega expõe o ciclo `ENFILEIRADO/DUPLICADO/RETENTANDO/ENTREGUE/FALHA_PERMANENTE`.
  - **Assinatura/documentos** trafegam por **referência ao object store** (`ReferenciaDocumento`), nunca BLOB ([ADR-0008](./0008-assinatura-provedor.md) / [ADR-0009](./0009-documentos-object-store.md)); saída PAdES/PDF com hash + id de transação.
  - **Segredos** nunca vazam: `CofreSegredos.ValorSegredo` redige o valor em `toString` e devolve **cópias defensivas** (não é `record`).
  - DTOs/enums/exceções de cada serviço ficam **aninhados no port** (um arquivo = um contrato), fiscalizados pelos guardrails (ArchUnit): sem framework no domínio, sem ponto flutuante.
- **Consequências:** RAZ-5/6/9/11/12 implementam adapters contra contratos já fixos, sem *retrofit*; a taxonomia de erros é estável e auditável (travada por teste de contrato); o domínio permanece puro (ADR-0002/0006, guardrails verdes). Custo: um contrato estável impõe **disciplina de versionamento** — toda mudança de assinatura/código de erro vira ADR.
- **Alternativas consideradas:** Ports por módulo ou em `application` (rejeitado: duplica e diverge — AGENTS.md manda interface de port no `domain`); erros como *strings*/códigos soltos sem tipo (rejeitado: não fiscalizável, sujeito a *drift*); documentos como BLOB no banco (rejeitado por ADR-0009).

---

[← ADRs](./README.md)
