# ADR-0007 · Read models / CQRS-lite para transparência e relatórios

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto:** A transparência (consulta pública, dados abertos) e os relatórios pesados (RREO/RGF/MSC) não podem onerar o *primary* OLTP do razão, e precisam de latência baixa e alto volume de leitura.
- **Decisão:** Leituras servidas por **read replicas / read models** derivados da base única (CQRS-lite). Índices de busca e visões públicas são **derivados reconstruíveis**, não fonte da verdade.
- **Consequências:** Escrita e leitura escalam separadas; portal e relatórios não competem com a escrituração. Consistência eventual entre escrita e read model (dentro do SLA de 1 dia útil). Derivados podem ser reconstruídos da base.
- **Alternativas consideradas:** Ler tudo do primary (rejeitado: contenção e risco de latência no fechamento); CQRS pleno com store de eventos separado (complexidade além do necessário no MVP).

---

[← ADRs](./README.md)
