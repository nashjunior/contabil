# ADR-0002 · Monólito modular (não microserviços no MVP)

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** Domínio fortemente transacional (atomicidade contábil) e *wedge* de custo baixo (municípios pequenos). Microserviços introduzem consistência distribuída e custo operacional desproporcionais no MVP.
- **Decisão:** **Monólito modular** — núcleo transacional (razão, execução, fechamento) num único deployable com uma transação ACID por fato; borda de publicação/integração assíncrona. Módulos com **fronteiras internas explícitas** (execução, razão, plataforma) para permitir extração futura.
- **Consequências:** Simplicidade operacional e correção; deploy barato por ente. Exige disciplina de fronteiras (imposta por ArchUnit — ver [guardrails](../README.md#8-guardrails-automatizados)).
- **Alternativas consideradas:** Microserviços desde o início (rejeitado: saga distribuída onde a lei pede atomicidade; custo alto no wedge).

---

[← ADRs](./README.md)
