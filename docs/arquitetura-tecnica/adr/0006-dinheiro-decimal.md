# ADR-0006 · Dinheiro em `NUMERIC`/`BigDecimal` (nunca float)

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** Erro de arredondamento em valor público é inaceitável (reprova em controle e conciliação). Ponto flutuante binário não representa decimais exatos.
- **Decisão:** Todo valor monetário é **`NUMERIC(18,2)`** no PostgreSQL e **`BigDecimal`** na JVM. **Proibido** `float`/`double` em domínio financeiro — imposto por regra de ArchUnit/Error Prone e pelo [guardião](../../../.claude/skills/guardiao/SKILL.md) (ver [guardrails](../README.md#8-guardrails-automatizados)).
- **Consequências:** Correção de centavos garantida; código explícito sobre escala/arredondamento (modo de arredondamento definido por política contábil). Nenhum trade-off relevante.
- **Alternativas consideradas:** `double` (rejeitado: imprecisão); inteiro em centavos (viável, mas `NUMERIC`/`BigDecimal` é mais legível e suporta agregações).

---

[← ADRs](./README.md)
