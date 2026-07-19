---
name: revisar-ddd
description: >-
  Revisa a INTEGRIDADE DO MODELO de domínio do SIAFIC entre os documentos: os módulos do
  monólito modular (execução, razão, plataforma) e a fronteira núcleo × estruturantes,
  agregados/entidades (docs/10 + razao-schema ↔ arquitetura-tecnica ↔ ADRs), o razão de
  dupla entrada como núcleo, cardinalidades (empenho→N liquidações→N pagamentos), e as
  invariantes que cruzam docs (dinheiro decimal, append-only/estorno, RLS/tenant, outbox,
  saldo derivado). Use ao revisar design de domínio, fronteiras de módulo, o modelo do razão
  ou a coerência entre docs. NÃO cobre mecânica de doc (links, citações, índice — isso é
  auditar-docs) nem código (guardiao-arquitetura). Apenas reporta.
allowed-tools: Read, Grep, Bash(grep:*), Bash(find:*), Bash(cat:*), Bash(ls:*), Agent
---

# Revisar integridade do modelo de domínio

Guardar a **coerência do modelo** do SIAFIC (Oberware) enquanto os docs evoluem. Fase de **docs** — a revisão é **doc-vs-doc de MODELO**: fronteiras de módulo, o razão como núcleo, cardinalidades e as invariantes que atravessam mais de um documento.

## Fronteira com `auditar-docs` (não duplicar)

- **`auditar-docs` = mecânica**: links/âncoras, citações legais, índice, fases, convenções.
- **`revisar-ddd` (esta) = semântica do modelo**: agregado declarado em 10 mas ausente no razao-schema; cardinalidade divergente entre fluxo 2 e o modelo; entidade de dado de ente sem `ente_id`; licitações/patrimônio tratados como núcleo (são estruturantes); saldo tratado como verdade (deve ser derivado).

Achado de mecânica visto de passagem: **aponte que é da `auditar-docs`** e siga.

## Fonte da verdade do modelo

| Doc | O que fixa |
| --- | --- |
| `docs/10-modelo-dados.md` | Entidades da execução da despesa, cardinalidades, tipos de empenho, o **razão contábil (núcleo)** |
| `docs/arquitetura-tecnica/razao-contabil-schema.md` | DDL do razão: contas PCASP, fato/lançamento, período, travas |
| `docs/arquitetura-tecnica/README.md` + `adr/` | Estilo (monólito modular), componentes, decisões (ADR-0001..0012) |
| `docs/11-plataforma-transversal.md` | Escopo núcleo × estruturantes; serviços de plataforma; contratos |
| `docs/05-regras-de-negocio.md` · `docs/04-fluxos.md` | Regras invioláveis e os fluxos |

Quando 10/razao-schema divergir de outro doc, **10 + razao-schema são a autoridade do modelo**; ADR é a autoridade da decisão de arquitetura. **Foco** (arg, ex.: `razao`, `despesa`, `tenant`, `estruturantes`): restringe.

## Invariantes a defender

### 1. Módulos e fronteira núcleo × estruturantes
- Os módulos são **execução / razão / plataforma** (monólito modular, ADR-0002). **Licitações, patrimônio, almoxarifado, folha, arrecadação são estruturantes** — fora do núcleo, integram por evento/ingestão. Doc que trate um estruturante como parte do núcleo = ❌.
- Novo agregado no razao-schema/arquitetura sem entrar em `docs/10` = ⚠️ (fronteira nova sem registro).

### 2. O razão é o núcleo
- Todo evento (empenho/liquidação/pagamento/receita) é **escriturado como fato + lançamentos** no razão (`docs/10 §razão`, razao-schema). Doc que trate execução como domínio lateral (não escriturado) = ❌.
- **Σdébito = Σcrédito**; **dinheiro decimal**; **append-only** (correção por estorno). Contradição em qualquer doc = ❌.

### 3. Cardinalidades (fluxo 2 ↔ modelo)
- `1 dotação → N empenhos`; `1 empenho → N liquidações`; `1 liquidação → N pagamentos`; liquidação tem base em **um** empenho (não N:N). Divergência entre `04-fluxos §2` e `10`/schema = ❌.
- Tipos de empenho (ordinário/estimativo/global) coerentes entre fluxo 2 e modelo.

### 4. Escopo de tenant / dado
- Entidade com dado de ente carrega `ente_id`; catálogo/tabela de domínio (PCASP, modalidade) pode ser global. Entidade de ente sem `ente_id` (ou tenant onde não cabe) = ❌ (cruza com ADR-0003).

### 5. Saldo derivado, não gravado como verdade (ADR-0007)
- Saldo é **calculado** dos lançamentos; saldo materializado tratado como fonte da verdade, sem ser cache reconstruível = ⚠️/❌.

### 6. Serviços de plataforma como contrato (doc 11)
- IAM/assinatura/auditoria/publicação/mascaramento são **serviços de plataforma** com interface (doc 11 §Contratos). Módulo que reimplementa um serviço transversal em vez de consumir o contrato = ⚠️.

## Passo 1 — Coletar estado real (buscas em paralelo)

1. `find docs -name "*.md" | sort`.
2. `grep -rEn 'Empenho|Liquidac|Pagamento|Fato|Lancamento|Dotac|Conta|PCASP|Periodo' docs/` — onde as entidades aparecem.
3. `grep -rEni 'ente_id|tenant|catalogo global|estruturante|nucleo' docs/` — escopo e fronteira.
4. `grep -rEni 'partidas dobradas|append-only|estorno|Σ|soma\(D\)|imutab' docs/` — invariantes do razão.
5. `grep -rEn 'N liquidac|N pagamento|1 empenho|cardinalidade' docs/` — cardinalidades.

**Em paralelo**, para revisão ampla, delegar a leitura estruturada a um subagente `Explore` (por doc: entidades e cardinalidades citadas, invariantes do razão afirmadas, o que é dito ser núcleo vs. estruturante). Aguardar antes de comparar.

## Passo 2 — Comparar e classificar
Cruzando 10 ↔ razao-schema ↔ arquitetura/ADRs ↔ 04/05/11, rode as invariantes 1–6. Antes de marcar violação, cheque se a divergência já é **decisão registrada** num ADR.

## Passo 3 — Relatório

```markdown
# Revisão de integridade do modelo — SIAFIC
> Data: <corrente> | Foco: <foco ou "geral"> | Autoridade: docs/10 + razao-schema + ADRs

## ❌ Violações de modelo
<o que quebra + doc:linha (origem) vs doc:linha (autoridade) + correção + ADR>
Se zero: "Nenhuma encontrada."

## ⚠️ Cheiros
<nome divergente do mesmo conceito, agregado citado de passagem, saldo materializado>

## ↪️ Fora do escopo (é da auditar-docs)
<mecânica vista de passagem>

## ✓ Consistente (amostra)
<2–4 aderências notáveis>

## Recomendações
<lista priorizada — doc:seção>
```

## Regras
- **Não modificar** — só reportar. **Não inventar** — "não localizado".
- **Não duplicar `auditar-docs`** — mecânica não é seu escopo.
- Priorizar violação de fronteira (núcleo × estruturante) e do razão sobre confirmações; relatório enxuto.
