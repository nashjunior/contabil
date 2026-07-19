# ADR-0011 · Idempotência ponta a ponta

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** Ingestão de estruturantes (ePING) e publicação (transparência/PNCP/SICONFI) podem reentregar a mesma mensagem; sem idempotência, um reprocessamento duplicaria fatos ou envios.
- **Decisão:** **Chave de idempotência** em toda entrada (inbox) e saída (outbox). O consumidor deduplica por chave; a mesma mensagem processada duas vezes tem efeito uma vez (*exactly-once* lógico). *Poison messages* vão para **DLQ** com alerta.
- **Consequências:** Reprocessamento seguro; retentativa com backoff sem risco de duplicação; base para a resiliência dos [fluxos 5/9/10](../../04-fluxos.md). Exige persistir chaves processadas (com janela/retenção).
- **Alternativas consideradas:** Confiar em "entrega exatamente uma vez" do broker (rejeitado: não existe garantia forte na prática); dedupe só na aplicação sem chave estável (frágil).

---

[← ADRs](./README.md)
