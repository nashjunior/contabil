# ADR-0013 · Persistência em lote com fail-soft e agregação de erros

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** A ingestão de estruturantes ([fluxo 5](../../04-fluxos.md#5-integração-com-sistemas-estruturantes)), a migração/carga de abertura ([doc 12](../../12-migracao.md)), a projeção de read-models e a publicação em lote processam **coleções grandes de registros independentes**. Persistir linha-a-linha é lento, e um item ruim **não** deve derrubar o lote inteiro nem abortar milhares de registros válidos.
- **Decisão:** Operações sobre coleções usam **batch** (insert/update/delete) e são **fail-soft**:
  - particiona a entrada em **`toInsert`** / **`toUpdate`** / **`toDelete`**;
  - processa em lote (JDBC batch, `INSERT … ON CONFLICT`);
  - agrega as falhas num **`errors`** (item + motivo) e **retorna quais não puderam** ser inseridos/atualizados/removidos.
  - **Unidade de atomicidade = o fato/registro individual**; o lote é fail-soft **entre** unidades, cada unidade permanece atômica.
- **Fronteira (crítica):** aplica-se à **borda** — ingestão, migração, read-models, publicação. **NÃO** se aplica à transação do **razão contábil**: um fato é **all-or-nothing** (Σdébito=Σcrédito); fail-soft parcial de partidas dobradas é **proibido** ([razao-schema](../razao-contabil-schema.md)).
- **Consequências:** throughput alto; falhas **observáveis e reprocessáveis** (idempotência [ADR-0011](./0011-idempotencia-ponta-a-ponta.md), DLQ); os rejeitados voltam pela trilha/relatório (fluxo 5 "rejeita, registra erro"; migração "relatório de divergências"). Trade-off: sucesso parcial por item — o chamador **deve tratar o `errors`** (não ignorar); e a operação retorna um resultado composto, não um booleano.
- **Consumo pela UI (se houver bulk):** operações em massa no back-office (importar planilha de credores/empenhos/restos a pagar, edição em lote) consomem o **mesmo contrato** via API e renderizam **sucesso parcial** a partir do `errors` — a UI não reinventa o padrão (o back provê, a UI se ajusta). Ter ou não bulk na UI é requisito de Produto a confirmar; a arquitetura já está coberta.
- **Alternativas consideradas:** row-by-row com abort no 1º erro (rejeitado: lento e frágil na ingestão/migração); all-or-nothing no lote inteiro (rejeitado: um item inválido bloqueia milhares válidos); fila item-a-item sem batch (rejeitado: perde o ganho de throughput do bulk).

---

[← ADRs](./README.md)
