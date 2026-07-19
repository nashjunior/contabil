---
name: auditar-docs
description: >-
  Audita a CONSISTÊNCIA INTERNA da documentação do SIAFIC (docs/, docs/transversais/,
  docs/arquitetura-tecnica/, adr/). Caça divergências mecânicas: links/âncoras quebrados
  (inclusive cross-folder ../ e transversais/), citações legais divergentes (Lei 4.320/64,
  LRF, Decreto 10.540/2020 e 11.644/2023, LGPD 13.709, Lei 14.133, LAI 12.527, 14.063,
  PCASP/MCASP), fases F0/F1/F2 fora da tabela-mestre do doc 11 ou do roadmap, índice do
  README desatualizado, convenções quebradas (H1, separador de tabela, Mermaid ASCII), e
  inventaria os "revalidar na fonte oficial" em aberto. Report-only, nunca modifica. Use ao
  pedir auditoria/consistência/checar divergências. NÃO é a revisão adversarial multi-lente
  (isso é o workflow revisao-multilente).
allowed-tools: Read, Grep, Bash(grep:*), Bash(find:*), Bash(cat:*), Bash(ls:*), Agent
---

# Auditar consistência da documentação

Auditoria **doc-vs-doc** e **doc-vs-fonte-legal** do SIAFIC (Oberware). Projeto ainda é **docs-only** (sem código de app). É diferente do workflow `revisao-multilente` (revisão adversarial de 4 lentes que sugere mudanças de conteúdo) — aqui é **mecânica**: o que está quebrado, defasado ou inconsistente.

Estrutura da doc:

| Pasta | Conteúdo |
| --- | --- |
| `docs/` | Núcleo: 01-visão, 02-base-legal, 03-arquitetura, 04-fluxos, 05-regras, 06-rastreabilidade, 07-roadmap, 08-mercado, 09-referências, 10-modelo-dados, 11-plataforma-transversal, 12-migração, 13-nfr |
| `docs/transversais/` | 01-assinatura, 02-pncp, 03-transparência, 04-lgpd, 05-acessibilidade |
| `docs/arquitetura-tecnica/` | README (técnico), razao-contabil-schema, `adr/` (0001–0012 + índice) |

**Autoridades de coerência:** [11-plataforma §tabela-mestre] (fases), [ADRs] (decisões), [02-base-legal]/[09-referências] (citações). **Foco** (se passado como arg, ex.: `transversais`, `citacoes`, `fases`, `links`): restringe as buscas.

## Passo 1 — Coletar estado real (buscas em paralelo)

Disparar simultaneamente (Bash, leitura):

1. `find docs -name "*.md" | sort` — inventário.
2. `grep -rEno '\]\((\.{1,2}/)*[a-z0-9/-]+\.md(#[^)]+)?\)' docs/` — links internos e âncoras.
3. `grep -rEn '^#{1,4} ' docs/` — cabeçalhos (validar âncoras/índice).
4. `grep -rEno 'Lei [0-9.]+/?[0-9]*|LC [0-9]+|Decreto [0-9.]+|art\. [0-9]+|LGPD|LRF|PCASP|MCASP|PNCP|SICONFI' docs/` — citações legais/siglas.
5. `grep -rn 'revalidar na fonte\|A VALIDAR\|TODO' docs/` — pendências abertas.
6. `grep -rEno 'F[0-4]' docs/07-roadmap.md docs/11-plataforma-transversal.md docs/transversais/*.md` — fases (cruzar com a tabela-mestre).
7. `grep -rn '|---' docs/` (esperado: nenhum, convenção é `| --- |`).

Para auditoria ampla, delegar a leitura estruturada a um subagente `Explore` (listas brutas por doc: entidades nomeadas, leis citadas com número, links, fases, "revalidar").

## Passo 2 — Comparar e classificar

1. **Links/âncoras** — cada `](…md#ancora)` aponta para arquivo existente (1.1) e cabeçalho real convertido em slug (1.3)? Inclui `../` e `transversais/`.
2. **Índice** — cada doc está no índice de `docs/README.md`? Algum arquivo fora do índice? (lacuna)
3. **Citações legais** — os números (Lei 4.320/1964, LC 101/2000, Decreto 10.540/2020, 11.644/2023, LGPD 13.709/2018, Lei 14.133/2021, LAI 12.527/2011, 14.063/2020) são idênticos onde aparecem? Divergência = stale (ex.: art. 2º vs art. 3º de "tempo real").
4. **Fases** — F0/F1/F2 das specs batem com a **tabela-mestre** do doc 11 e o roadmap?
5. **Convenções** — H1 na 1ª linha; separador `| --- |`; Mermaid ASCII e balanceado; marcações `[OBRIGATÓRIO]`/`[PRODUTO]` coerentes.
6. **Escopo núcleo × estruturantes** — algum doc trata licitações/patrimônio/folha como núcleo (deveria ser estruturante)?
7. **"Revalidar na fonte"** — inventariar os pontos legais ainda não confirmados.

## Passo 3 — Relatório (≤ 60 linhas)

```markdown
# Auditoria de consistência — docs SIAFIC
> Data: <corrente> | Foco: <foco ou "geral">

## ⚠️ Inconsistente / stale
<doc:linha → o que afirma vs. realidade (link quebrado, citação divergente, fase fora da tabela-mestre)>
Se zero: "Nenhum encontrado."

## 🆕 Lacuna
<termo/fonte/doc/fase → onde deveria estar registrado>
Se zero: "Nenhum encontrado."

## 🕓 Revalidar na fonte oficial (inventário)
<doc:linha → ponto legal ainda não confirmado>

## ✓ Consistente (amostra)
<3–5 itens que batem>

## Recomendações
<lista priorizada — arquivo:seção>
```

## Regras

- **Não modificar** — só reportar. **Não inventar** — "não localizado" em vez de inferir.
- Priorizar stale e lacunas sobre confirmações; relatório enxuto.
- Contexto: **sem código/git** — auditoria é doc-vs-doc e doc-vs-fonte-legal.
- Divergência de **conteúdo/estratégia** (não mecânica) → é do workflow `revisao-multilente`, não desta skill.
