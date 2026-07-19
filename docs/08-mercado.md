# Cenário de mercado e concorrência

[← Índice](./README.md)

> **Aviso:** análise estratégica para fins de estudo. Nomes de fornecedores, sistemas e ordens de grandeza são ilustrativos e devem ser confrontados com pesquisa de mercado atualizada (editais, contratos publicados, dados dos Tribunais de Contas e do SICONFI) antes de qualquer decisão comercial.

## Panorama

O SIAFIC **não é um mercado greenfield**. Estados e municípios já operavam sistemas de execução orçamentário-financeira muito antes do Decreto nº 10.540/2020. O decreto não criou a demanda por software; ele **impôs a unificação** — um único sistema por ente, compartilhado entre todos os Poderes e órgãos — e um padrão mínimo de qualidade. O efeito prático foi obrigar entes a migrar ou adaptar sistemas existentes, movimentando o mercado, mas **em torno de incumbentes já consolidados**.

Entrar aqui é disputar substituição de fornecedor, não conquista de espaço vazio.

## Incumbentes

### Municípios (mais de 5.500 entes)

Mercado dominado por um punhado de fornecedores de ERP público, com forte presença regional:

| Fornecedor | Origem/força |
| --- | --- |
| **Betha Sistemas** | SC — grande base nacional |
| **IPM Sistemas** | SC — ampla cobertura |
| **Elotech** | PR |
| **GOVBR (GRP)** | consolidação de players (ex-Governança Brasil/Pública) |
| **Fiorilli** | SP |
| **Consist / Público, Thema, Nutti, Sonner, entre outros** | presença regional |

Esses fornecedores adaptaram seus produtos para "modo SIAFIC" quando o decreto entrou em vigor, aproveitando a base instalada.

### Estados

Mistura de **sistemas próprios** (desenvolvidos por Secretarias de Fazenda) e soluções licenciadas entre entes:

| Sistema | Ente(s) |
| --- | --- |
| **SIGEF** | Santa Catarina (licenciado a outros estados) |
| **FIPLAN** | Bahia e consórcio de estados |
| **SIAFE-RJ / SIAFE-ES** | Rio de Janeiro, Espírito Santo |
| **SIAFEM** (legado) | vários estados historicamente |
| Soluções de fornecedores privados | estados diversos |

### Solução pública

O **Tesouro Nacional** disponibilizou solução gratuita de SIAFIC voltada especialmente a **entes de menor porte** sem sistema próprio — um concorrente "custo zero" relevante na faixa dos municípios pequenos.

## Barreiras de entrada

- **Custo de troca alto** — dados históricos, saldos e séries contábeis presos ao sistema atual; migração é arriscada e cara.
- **Integração com Tribunais de Contas** — cada TCE tem leiautes próprios (ex.: SIM-AM, e-TCM, layouts de remessa). Homologar em cada TC é trabalhoso e é pré-requisito comercial.
- **Contratos plurianuais e relacionamento** — licitações com incumbente entrincheirado; ciclos longos.
- **Confiança e responsabilização** — falha no sistema expõe o gestor a sanção (LRF); aversão a fornecedor não comprovado.
- **Capilaridade de suporte** — municípios exigem atendimento local/contábil, não só software.

## Janelas de oportunidade (wedge)

- **Municípios pequenos e médios** com ferramentas fracas, caras ou defasadas — menor custo de troca e maior dor.
- **Consórcios públicos e associações de municípios** — venda agregada, dilui custo de aquisição e suporte.
- **Migrações forçadas** por exigências novas de Tribunais de Contas ou insatisfação com o incumbente.
- **Conformidade por arquitetura** — vender o piso legal como garantido *por construção* (base única, imutabilidade, trilha), reduzindo o risco de responsabilização do gestor. Ver [Princípios](./01-visao-e-principios.md) e [Arquitetura](./03-arquitetura.md).

## Diferenciação acima do piso legal

O mínimo do decreto (`[OBRIGATÓRIO]`) é commodity — todos os incumbentes alegam atendê-lo. A disputa real está nos diferenciais `[PRODUTO]` já mapeados nos [fluxos](./04-fluxos.md):

- **Transparência e dados abertos** de verdade — busca, filtros e API pública ([fluxo 9](./04-fluxos.md#9-transparência-em-tempo-real)).
- **Relatórios de exceção proativos** — acessos privilegiados, operações fora de alçada ([fluxo 7](./04-fluxos.md#7-trilha-de-auditoria-e-vedações)).
- **Validação prévia e trilha de envio ao SICONFI** ([fluxo 10](./04-fluxos.md#10-consolidação-nacional-siconfi)).
- **Monitoramento e reprocessamento de integrações** com estruturantes ([fluxo 5](./04-fluxos.md#5-integração-com-sistemas-estruturantes)).
- **Experiência e arquitetura modernas** — cloud, API-first, UX — contra sistemas legados.

> **Tese de entrada:** o piso legal abre a porta (todo ente precisa se adequar), mas não diferencia. O produto ganha onde o incumbente é fraco — usabilidade, transparência real, integração aberta e conformidade garantida por design — começando pelos entes de menor custo de troca.

---

[← Roadmap](./07-roadmap.md) · [Índice](./README.md) · [Referências →](./09-referencias.md)
