# ADR-0025 · Building blocks táticos de DDD: vocabulário de consulta, sem hierarquia base

- **Status:** Aceita
- **Data:** 2026-07-26
- **Contexto:** RAZ-90 avaliou importar building blocks táticos de DDD vindos de outro projeto (`Pagination`, `SearchQuery`, `Sorts`, `SortType`, `ValueObject`, `Identifier`, `Entity`, `AggregateRoot`, `AuditEntity`). O brief original sugeria usar o número ADR-0022, mas esse número já está ocupado por [ADR-0022 Lote de pagamento](./0022-lote-pagamento-contrato-api-execucao.md). Para preservar a numeração histórica e imutável dos ADRs, esta decisão fica registrada como ADR-0025.
- **Decisão:**
  1. Adotar apenas o grupo de consulta/leitura como vocabulário explícito em pt-BR no shared kernel (`plataforma-domain`): `Paginacao<T>`, `ConsultaPaginada<F>`, `Ordenacao`, `Direcao` e value objects auxiliares de filtro, como `JanelaConsulta`.
  2. O tenant não entra dentro do critério genérico de busca. Segue explícito no método do port/use case quando o dado é escopado por ente, e a borda/RLS validam o escopo conforme [ADR-0015](./0015-atribuicao-tenant-explicita-no-contrato.md) e [ADR-0003](./0003-multi-tenant-rls.md).
  3. Rejeitar em bloco hierarquias táticas genéricas: `ValueObject`, `Identifier`, `Entity`, `AggregateRoot`, `AuditEntity`, `BaseEntity`, `BaseRepository`, `Specification` e equivalentes em pt-BR.
  4. Validação continua fail-fast por exceção de domínio/contrato. Não adotar `ValidationHandler`/notification pattern no domínio.
  5. Soft-delete (`deletedAt`, `delete lógico`) permanece proibido no domínio do razão e na trilha append-only; correção contábil é por estorno e evento novo, não mutação.
- **Consequências:** O shared kernel passa a ter uma linguagem estável para consultas e read models sem impor herança a agregados, value objects ou repositórios. A leitura da trilha de auditoria usa esse contrato como primeiro caso real (`ConsultaPaginada<FiltroAuditoria> -> Paginacao<EventoAuditoria>`). A arquitetura fica protegida por guardrail ArchUnit que rejeita nomes de building blocks de base genérica no pacote de domínio.
- **Alternativas consideradas:**
  - Importar a hierarquia completa do template externo. Rejeitada: colide com records como value objects, agregados `final class`, validação por exceção, tempo via `Clock` injetado e append-only do razão/trilha.
  - Criar os tipos de consulta sem uso imediato. Rejeitada como direção geral, mas RAZ-90 agora tem um caso real de leitura transversal: auditoria. Por isso o contrato entrou vinculado a esse port.
  - Reaproveitar o número ADR-0022. Rejeitada: ADR-0022 já é uma decisão aceita de lote de pagamento; renumerar ou sobrescrever quebraria rastreabilidade.

[← ADRs](./README.md) · [ADR-0003](./0003-multi-tenant-rls.md) · [ADR-0015](./0015-atribuicao-tenant-explicita-no-contrato.md) · [ADR-0022](./0022-lote-pagamento-contrato-api-execucao.md)
