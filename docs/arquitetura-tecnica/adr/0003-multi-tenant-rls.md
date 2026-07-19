# ADR-0003 · Multi-tenant por `tenant_id` + RLS deny-by-default

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** Um SIAFIC serve muitos entes; um vazamento cross-tenant reprova no controle externo. O wedge pede um pool barato para municípios pequenos, mas entes grandes podem exigir isolamento físico.
- **Decisão:** Isolamento por `ente_id` + **Row Level Security forçada, deny-by-default** ([schema §Trava 4](../razao-contabil-schema.md#trava-4--isolamento-multi-ente-rls-deny-by-default)); a aplicação seta `app.ente_id` por transação. **Schema/DB dedicado** disponível para entes grandes.
- **Consequências:** Isolamento auditável e barato no pool; caminho de escalonamento por porte. Exige **teste de vazamento cross-tenant no CI** (bloqueante).
- **Alternativas consideradas:** Isolamento só na aplicação (rejeitado: um bug de filtro vaza dados); um banco por ente sempre (caro no wedge).

---

[← ADRs](./README.md)
