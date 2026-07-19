# ADR-0005 · Trilha append-only com hash-chain em store segregado

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** O Decreto 10.540/2020 (arts. 9º e 12) exige trilha imutável e íntegra de toda operação — inclusive **leitura/exportação de PII**. "Imutável" afirmado sem mecanismo não sobrevive à inspeção do controle externo.
- **Decisão:** Trilha **append-only** com **encadeamento de hash** (cada evento carrega o hash do anterior), em **store segregado** do razão, com replicação externa e verificação periódica de integridade. Retenção parametrizável alinhada à guarda contábil.
- **Consequências:** Adulteração detectável; a escrita do razão não é serializada pela trilha (store separado). Custo de operar/backupar um segundo store.
- **Alternativas consideradas:** Log na mesma base (rejeitado: acopla e não prova imutabilidade); confiar só em permissões (insuficiente para o controle externo).

---

[← ADRs](./README.md)
