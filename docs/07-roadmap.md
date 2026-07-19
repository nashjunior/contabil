# Roadmap

[← Índice](./README.md)

> **Fonte única das fases do programa.** As fases globais **F0–F4** abaixo são a referência; a maturidade por serviço de plataforma (M0/M1/M2) é mapeada a estas fases na [tabela-mestre do doc 11](./11-plataforma-transversal.md). **Go-live = fim do F1** (um ente real em produção, sobrevivendo ao ciclo de controle externo).

| Fase | Entregas | Objetivo |
| --- | --- | --- |
| **F0 — Fundações** | Base única + **razão contábil** (partidas dobradas, PCASP); identidade CPF/certificado + RBAC; **trilha imutável** (hash-chain); **[piso de segurança F0](./13-nfr-e-operacao.md#piso-de-segurança-f0)**; **assinatura gov.br avançada**; **interfaces** dos serviços de plataforma + motor de publicação mínimo | Núcleo íntegro e auditável; plataforma com contratos definidos |
| **F1 — MVP de conformidade + prestação de contas (go-live)** | Execução orçamentária/financeira/contábil completa; **restos a pagar + trava LRF art. 42**; fechamento; **transparência ativa + dados abertos CSV/JSON `[OBRIGATÓRIO]`**; **prestação de contas: MSC (Portaria 642) + remessa ao TCE + RREO/RGF/DCA nos prazos**; **assinatura qualificada ICP-Brasil**; **[migração/implantação do ente-piloto](./12-migracao.md)** (bloqueante de go-live) | Um ente real opera e **sobrevive ao 1º ciclo de controle externo** |
| **F2 — Integração e consolidação** | Conectores com estruturantes (folha, tributos, patrimônio, licitações); **gate PNCP bloqueante** (art. 94, com status integrado); conciliação bancária; extratores SICONFI ampliados; **remuneração individualizada via folha** | Eliminar retrabalho/redigitação; consolidação plena |
| **F3 — Valor e inteligência** | Painéis, relatórios de exceção, alertas proativos; dados abertos avançados (versionamento, download de bases); alinhamento à **EBT 360 (CGU)** | Produtividade e controle proativo |
| **F4 — Escala e evolução** | Planejamento (**elaboração** PPA/LDO/LOA), BI, desempenho, multi-ente em escala | Cobertura ampliada do ciclo fiscal |

> **Nota de resequenciamento (revisão multi-lente):** a plataforma pesada foi rebaixada a *interface + implementação mínima* no F0; a **prestação de contas** (MSC/TCE/relatórios LRF) e os **restos a pagar** subiram para o **MVP/go-live (F1)** — sem elas o ente é reprovado no controle externo (LRF art. 73-C → art. 23, §3º, I). Assinatura qualificada e gate PNCP bloqueante saíram do F0 (gold-plating/sequência inviável).

---

[← Rastreabilidade](./06-rastreabilidade.md) · [Índice](./README.md) · [Cenário de mercado →](./08-mercado.md)
