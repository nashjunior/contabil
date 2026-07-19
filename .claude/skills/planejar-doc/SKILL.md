---
name: planejar-doc
description: >-
  Produz um plano assertivo ANTES de editar a documentação do SIAFIC. Mapeia os docs
  pertinentes (docs/ + transversais/ + arquitetura-tecnica/), confronta as fontes tratando
  os ADRs e o doc 11 (tabela-mestre de fases) como autoridade, e entrega um plano de edição
  concreto — com doc:§, cap de 4 passos, sem hedge — mais o ferramental de revisão a rodar
  depois (revisar-ddd, auditar-docs, guardiao) e a decisão a registrar (novo ADR?). É a
  PORTA DA FRENTE do loop de edição. Use ao criar/alterar um doc, propor mudança de
  modelo/fluxo/decisão, ou resolver um "revalidar na fonte". NÃO edita e NÃO revisa — planeja.
allowed-tools: Read, Grep, Bash(grep:*), Bash(find:*), Bash(cat:*), Bash(ls:*), Agent
---

# Planejar mudança de documentação

Produzir um **plano fundamentado nas fontes canônicas**, antes de tocar qualquer arquivo. O SIAFIC está em fase de **docs** — a verdade é o doc; o plano nasce das fontes certas e aponta o caminho, não implementa.

Mudança: `$ARGUMENTS`

## Princípio — a verdade tem hierarquia de autoridade

Quando as fontes divergem, o plano **segue a autoridade**, não a maioria:

1. **ADRs** (`docs/arquitetura-tecnica/adr/`) — decisões de arquitetura. Autoridade sobre estilo/tecnologia/estrutura.
2. **`docs/11-plataforma-transversal.md`** (tabela-mestre de fases; escopo núcleo × estruturantes) e **`docs/10-modelo-dados.md` + `razao-contabil-schema.md`** (modelo/DDL) — autoridade de modelo e faseamento.
3. **`docs/02-base-legal.md` / `docs/09-referencias.md`** — a pilha normativa; citação não confirmada fica **"revalidar na fonte oficial"**.
4. Fontes externas (Planalto, gov.br, ITI, STN, PNCP) — verdade legal/técnica. Confirmar via a skill `pesquisar-fonte`.

## Regras

- **Sem prosa** ("vou agora…"). Output = as seções do Passo 4.
- **Sem hedge** ("talvez"). Decisão concreta ou pergunta direta.
- Cada passo tem `doc:§` (ou `doc:linha`) ou marca `(NOVO)`.
- **Cap de 4 passos.** Se não couber, pedir para dividir.
- Divergência entre docs → passo *"alinhar doc X ao ADR/§Y"*.
- Sempre listar o **ferramental depois** (revisão + registro).
- **Não editar** — sem Write/Edit.

## Fluxo

### 1. Mapear docs pertinentes (máx 4)
Usar o índice de `docs/README.md` para selecionar **só** os docs pertinentes (considere as três pastas e cross-refs `../`). Listar sem ler ainda.

### 2. Mapear estado atual com `Explore`
Disparar **uma** chamada `Explore`:
> "Nos docs `<lista>` do SIAFIC (Oberware), sintetize sem análise: (1) como `<mudança>` é descrita hoje e em qual doc/§ é **definida**; (2) quais docs a **referenciam** (inclusive `../`); (3) convenções específicas ali. Liste com `doc:§`. 5–10 linhas."

### 3. Read parcial
Para cada doc, ler **só a seção relevante** (achar a `§` com `Grep`; usar `limit`/`offset` se > 200 linhas). Nunca o doc inteiro.

### 4. Confrontar e planejar — output final, exatamente este formato

```markdown
## Fontes consultadas
- doc:§ → ✓ confirma
- doc:§ vs doc:§ → ✗ divergem: <o que a autoridade (ADR/11/10) diz> vs <o outro em doc:linha>

## Estado atual
<3–5 linhas, sem prosa>

## Plano
1. <doc:§ ou (NOVO)> — <ação concreta em 1 linha>
2. ...
3. ...
4. ...

## Fora do escopo
- <coisa relacionada que não será tocada>

## Ferramental depois
### Revisão (após editar)
- /<skill ou guardião> — <por quê, nesta mudança>
### Registro
- ADR → <criar novo ADR? qual decisão> (ou "nenhum")
```

### 5. Tabela de ferramental (consultar antes do Passo 4)

| Ferramenta | Tipo | Listar se o plano toca… |
| --- | --- | --- |
| `/revisar-ddd` | revisão | modelo de domínio, fronteira de módulo, razão, cardinalidades, escopo núcleo × estruturantes |
| `/auditar-docs` | revisão | links/âncoras, citações legais, índice, fases, convenções |
| `guardiao-*` (subagente) | revisão | se já houver **código** tocado |
| `/adr` | registro | a mudança **decide** algo de arquitetura (nova decisão ou supersede) |
| `/pesquisar-fonte` | fonte | depende de confirmar lei/contrato externo antes de editar |

## Quando NÃO usar (dizer que é overkill, editar direto)

- Mudança trivial (uma frase, um número/data) — vai direto.
- Correção mecânica (link, typo) — vai direto, ou é caso de `/auditar-docs`.
- Pergunta de leitura ("onde está X?") — responder direto.
