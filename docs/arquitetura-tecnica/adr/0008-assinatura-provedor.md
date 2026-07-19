# ADR-0008 · Assinatura via abstração de provedor (gov.br → ICP-Brasil)

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** Empenhos, contratos e ordens bancárias exigem validade jurídica (Lei 14.063/2020, MP 2.200-2). Construir cripto/CA própria é caro e arriscado; o nível exigido varia por ente × tipo de documento.
- **Decisão:** Assinar por uma **abstração de provedor** (interface única, provedores plugáveis): **gov.br avançada** (F0) e **ICP-Brasil qualificada** (F1, via `icp_brasil`/nuvem). Saída **PAdES/PDF**; validação delegada ao ITI. Detalhe na [spec 01](../../transversais/01-assinatura-eletronica.md).
- **Consequências:** Sem motor criptográfico próprio; troca/adição de provedor sem reescrever fluxos de empenho/contrato; nível parametrizável por ente. Dependência da disponibilidade do gov.br (tratada como estado "pendente de assinatura", nunca parcial).
- **Alternativas consideradas:** Implementar assinatura própria (rejeitado: risco jurídico e de segurança); acoplar a um único provedor (rejeitado: perde flexibilidade e o subconjunto que exige qualificada).

---

[← ADRs](./README.md)
