# ADR-0012 · Stack primária = JVM (Java/Kotlin, Spring Boot)

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** O núcleo exige correção financeira (`BigDecimal`, transações maduras), integração com **assinatura ICP-Brasil** e um pool de contratação viável no setor público brasileiro. Evitar *polyglot sprawl* num time pequeno (wedge de custo baixo).
- **Decisão:** **JVM (Java/Kotlin, Spring Boot)** como stack primária de back-end — decisão ratificada. O desempate frente ao .NET foi o **ecossistema ICP-Brasil/PAdES** (bibliotecas oficiais de assinatura em Java: BouncyCastle, iText, Demoiselle) somado ao maior pool de contratação. Front-ends em **TypeScript**; dados/migração em **Python**; **Go** só se os workers de integração justificarem um 2º runtime.
- **Consequências:** Um runtime transacional para núcleo + API + relatórios; ferramental de guardião maduro (**ArchUnit**, Error Prone, SpotBugs — ver [guardrails](../README.md#8-guardrails-automatizados)). Kotlin reduz cerimônia frente ao Java (escolha Kotlin×Java fica a critério do time).
- **Alternativas consideradas:** **.NET/C#** (co-equivalente técnico; preterido pelo ecossistema ICP-Brasil e pool gov); Go/Elixir para o núcleo (rejeitado: modelagem contábil/financeira menos convencional); Node/Python no núcleo (rejeitado: tipagem dinâmica é passivo na correção de dinheiro).

---

[← ADRs](./README.md)
