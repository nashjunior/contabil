# ADR-0010 · Single-writer (Postgres primary) com failover fencing

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** O razão exige uma ordem de escrita consistente e numeração sequencial cronológica; escrita concorrente em múltiplos masters causaria conflito e split-brain.
- **Decisão:** **Um único escritor** (Postgres *primary*); réplicas são somente-leitura. Failover promove uma réplica com **fencing** (o antigo primary é isolado antes da promoção). Sem multi-master.
- **Consequências:** Consistência forte de escrita; sem conflito de numeração. RTO depende do tempo de failover (metas no [NFR](../../13-nfr-e-operacao.md)). Escala de escrita vertical + particionamento.
- **Alternativas consideradas:** Multi-master (rejeitado: split-brain e conflito no razão); escrita em réplica (rejeitado: viola single-writer).

---

[← ADRs](./README.md)
