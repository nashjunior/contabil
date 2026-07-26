# SIAFIC — Sistema Único e Integrado de Execução Orçamentária, Administração Financeira e Controle

> Plataforma de gestão fiscal para entes federativos (União, Estados, DF e Municípios) e seus Poderes e órgãos, em conformidade com o **Decreto nº 10.540/2020**.

**Documento de produto · v1.0**

O SIAFIC é o **sistema único de registro da vida financeira do ente federativo**: uma base contábil única, integrada e em tempo real, na qual todo fato é registrado uma só vez, é imutável após consolidado, é integralmente auditável e flui automaticamente para a transparência ativa e para a consolidação nacional.

---

## Documentação

Organizada por tema (o número no arquivo é só o prefixo; a leitura segue os grupos abaixo):

### Fundamentos

- [01 · Visão e princípios](./01-visao-e-principios.md) — visão do produto e princípios norteadores
- [02 · Base legal](./02-base-legal.md) — pilha normativa (núcleo + transversais) e convenção `[OBRIGATÓRIO]`/`[PRODUTO]`
- [03 · Arquitetura conceitual](./03-arquitetura.md) — base única e integrada por design
- [Arquitetura técnica](./arquitetura-tecnica/) — componentes de infra, linguagem ideal por situação, ADRs e stress test lógico (caso de uso × falha)
- [11 · Plataforma e requisitos transversais](./11-plataforma-transversal.md) — serviços de base herdados pelos módulos; escopo núcleo × estruturantes
  - [Assinatura eletrônica](./transversais/01-assinatura-eletronica.md) · [PNCP](./transversais/02-pncp.md) · [Transparência](./transversais/03-transparencia.md) · [LGPD](./transversais/04-lgpd.md) · [Acessibilidade](./transversais/05-acessibilidade.md)

### Especificação funcional

- [04 · Fluxos do sistema](./04-fluxos.md) — os 10 fluxos (diagramas Mermaid)
- [05 · Regras de negócio](./05-regras-de-negocio.md) — regras invioláveis impostas pelo sistema
- [10 · Modelo de dados (anexo)](./10-modelo-dados.md) — ciclo da despesa: entidades, cardinalidades, tipos de empenho

### Implementação e operação

- [12 · Migração e implantação](./12-migracao.md) — onboarding do ente (saldos, restos a pagar, de-para PCASP); bloqueante de go-live
- [13 · NFR e operação](./13-nfr-e-operacao.md) — disponibilidade, RPO/RTO, DR/BCP, backup imutável e o **piso de segurança F0**
- [Operação](./operacao/) — runbooks operacionais e evidências do piso F0

### Conformidade

- [06 · Rastreabilidade legal → requisito](./06-rastreabilidade.md) — matriz norma → requisito de produto

### Estratégia e gestão

- [07 · Roadmap](./07-roadmap.md) — fases F0 a F4
- [08 · Cenário de mercado](./08-mercado.md) — incumbentes, barreiras de entrada e diferenciação

### Referência

- [09 · Referências normativas](./09-referencias.md) — dispositivos legais citados

### Fluxos do sistema

Atalhos diretos para os diagramas em [04-fluxos.md](./04-fluxos.md):

- [1. Visão macro](./04-fluxos.md#1-visão-macro)
- [2. Execução da despesa](./04-fluxos.md#2-execução-da-despesa)
- [3. Execução da receita](./04-fluxos.md#3-execução-da-receita)
- [4. Escrituração e correção por estorno](./04-fluxos.md#4-escrituração-e-correção-por-estorno)
- [5. Integração com estruturantes](./04-fluxos.md#5-integração-com-sistemas-estruturantes)
- [6. Acesso e autenticação](./04-fluxos.md#6-acesso-e-autenticação)
- [7. Trilha de auditoria e vedações](./04-fluxos.md#7-trilha-de-auditoria-e-vedações)
- [8. Fechamento de período](./04-fluxos.md#8-fechamento-de-período)
- [9. Transparência em tempo real](./04-fluxos.md#9-transparência-em-tempo-real)
- [10. Consolidação nacional (SICONFI)](./04-fluxos.md#10-consolidação-nacional-siconfi)

---

## Documentos do produto

| Documento | Descrição | Status |
| --- | --- | --- |
| `README.md` | Este arquivo — índice e visão geral | ✅ |
| `PRD_SIAFIC.docx` | Documento de Requisitos de Produto (visão, personas, escopo, requisitos, rastreabilidade) | ✅ |
| [Modelo de dados](./10-modelo-dados.md) | Dicionário das entidades da base única | 🟡 parcial (ciclo da despesa) |
| User stories + critérios de aceite | Backlog detalhado (Gherkin) | ⏳ pendente |
| [Máquinas de estado](./10-modelo-dados.md#ciclo-de-vida-do-empenho) | Ciclo de vida do empenho e do período | 🟡 parcial (empenho) |
| Matriz de perfis (RBAC) | Segregação de funções por persona × operação | ✅ código F0 (`ServicoIdentidadeGovBrIcp`) |
| [Fluxo do operador + contrato de API — execução (F1)](./arquitetura-tecnica/fluxo-execucao-operador-contrato-api.md) | Empenho→liquidação→pagamento do ponto de vista do operador, segregação de funções, wireframes descritos e contrato de API (decisão de lote em [ADR-0022](./arquitetura-tecnica/adr/0022-lote-pagamento-contrato-api-execucao.md)) | 🟡 proposto (RAZ-79, cruzar com Aurélio) |
| [Design system SIAFIC — tokens e componentes (F1)](./arquitetura-tecnica/design-system-tokens-componentes.md) | Biblioteca Figma (tokens cor/tipografia/espaçamento/elevação + 10 componentes-núcleo); mapeamento token→uso; decisões estruturais em [ADR-0026](./arquitetura-tecnica/adr/0026-design-system-figma-decisoes-estruturais.md) | 🟡 proposto (RAZ-100, cruzar com Aurélio) |

---

> **Nota:** documento de produto para fins de especificação e estudo. Os dispositivos legais devem ser confrontados com o texto oficial vigente antes de decisões de conformidade.
