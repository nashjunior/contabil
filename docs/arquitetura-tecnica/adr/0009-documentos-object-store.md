# ADR-0009 · Documentos assinados em object store/GED (não BLOB no banco)

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** PDFs assinados (PAdES) de empenhos, contratos e ordens bancárias têm guarda longa e volume crescente; guardá-los como BLOB no banco incha o razão e encarece o backup transacional.
- **Decisão:** Documentos vivem num **object store / GED**, cifrados em repouso, referenciados por **FK** a partir do fato (entidade `DOCUMENTO_ASSINADO` no [modelo de dados](../../10-modelo-dados.md)); o banco guarda só metadados (hash, id de transação, uri).
- **Consequências:** Banco enxuto; storage e backup de documentos dimensionados à parte. Consistência documento↔registro por referência + verificação de integridade (hash).
- **Alternativas consideradas:** BLOB no PostgreSQL (rejeitado: incha o razão e o backup); sistema de arquivos local (rejeitado: sem redundância/retenção adequadas).

---

[← ADRs](./README.md)
