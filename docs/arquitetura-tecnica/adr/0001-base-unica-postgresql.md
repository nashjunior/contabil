# ADR-0001 · Base contábil única em PostgreSQL relacional

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** O núcleo do SIAFIC é fortemente relacional e transacional — integridade referencial, invariante Σdébito=Σcrédito, constraints de saldo, período e imutabilidade. A lei exige base única, íntegra e auditável ([03](../../03-arquitetura.md), Decreto 10.540/2020).
- **Decisão:** A fonte da verdade é um banco **relacional PostgreSQL** (ACID), não NoSQL. Travas contábeis impostas no banco ([schema do razão](../razao-contabil-schema.md)).
- **Consequências:** Correção e auditabilidade fortes; ferramental maduro (RLS, constraint triggers). Escala de escrita limitada ao *primary* → mitigada por read replicas ([ADR-0007](./0007-read-models-cqrs.md)) e particionamento por `exercicio`/`ente_id`.
- **Alternativas consideradas:** NoSQL (rejeitado: perde ACID e integridade multi-tabela); event-sourcing puro (complexidade sem ganho para o domínio contábil clássico).

---

[← ADRs](./README.md)
