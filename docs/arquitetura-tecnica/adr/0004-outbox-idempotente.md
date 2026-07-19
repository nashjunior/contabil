# ADR-0004 · Publicação/integração via outbox transacional + broker idempotente

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** Um fato precisa fluir para transparência, PNCP e SICONFI **sem se perder nem duplicar**, mas essas saídas toleram consistência eventual (transparência ≤ 1 dia útil; consolidação em lote).
- **Decisão:** O efeito externo grava um evento no **outbox na mesma transação** do fato; workers relêem e despacham via **broker**, com **idempotência** no consumidor (chave de idempotência). Nenhuma chamada externa síncrona dentro da transação do fato.
- **Consequências:** Atomicidade entre fato e intenção de publicar; reprocessamento seguro; desacoplamento. Consistência eventual na borda (aceitável pelos SLAs legais). Exige DLQ para *poison messages*.
- **Alternativas consideradas:** Chamada externa síncrona na transação (rejeitado: acopla a transação contábil à disponibilidade de terceiros); 2PC distribuído (complexidade desnecessária).

---

[← ADRs](./README.md)
