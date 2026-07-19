---
name: pesquisar-fonte
description: >-
  Vai à fonte externa oficial (legislação — Lei 4.320/64, LRF, Decreto 10.540/2020, LGPD
  13.709, Lei 14.133, LAI, 14.063; normas STN/PCASP/MCASP; Swagger do PNCP; manuais gov.br/
  ITI/ANPD) para resolver uma pendência CONCRETA do SIAFIC: um "revalidar na fonte oficial",
  uma citação de doc a confirmar, ou um contrato de integração (PNCP/SICONFI/gov.br).
  Classifica a pergunta contra o doc alvo, checa o que já existe no repo antes de pesquisar
  fora, distingue fonte primária/oficial de secundária (citação legal só conta com fonte
  primária), e aponta o doc:§ a atualizar. Para levantamento amplo multi-fonte, recomenda
  /deep-research. Report-only — a edição é do planejar-doc. Use ao pedir para pesquisar/
  confirmar algo externo que vá alimentar um doc.
allowed-tools: Read, Grep, Bash(grep:*), Bash(find:*), Bash(cat:*), WebSearch, WebFetch
---

# Pesquisar fonte externa

Ir à fonte externa para alimentar uma pendência **concreta** do SIAFIC (Oberware) — não pesquisa genérica. É a ponte entre o mundo de fora (Planalto, gov.br, STN, PNCP) e o doc/citação certo; **não edita** nada — entrega o achado para o `planejar-doc` aplicar.

> Contexto do projeto: a pesquisa original teve o **Planalto intermitente**, então há vários "revalidar na fonte oficial" nos docs. Esta skill é o instrumento para fechá-los.

## Passo 1 — Classificar a pergunta

| Tópico | Doc(s) alvo | Fonte primária |
| --- | --- | --- |
| Direito financeiro/contábil (Lei 4.320/64, LRF, PCASP/MCASP/DCASP, prazos RREO/RGF/DCA) | `docs/02-base-legal`, `docs/06-rastreabilidade`, `docs/10`/schema | planalto.gov.br · tesourotransparente.gov.br (STN/MCASP) |
| SIAFIC (Decreto 10.540/2020, 11.644/2023) | `docs/02`, `docs/11` | planalto.gov.br |
| LGPD / transparência (13.709, LC 131, LAI 12.527, Dec. 7.185, ANPD, STF Tema 483) | `docs/transversais/04-lgpd`, `docs/transversais/03-transparencia` | planalto.gov.br · gov.br/anpd · portal.stf.jus.br |
| Assinatura (14.063, MP 2.200-2, ICP-Brasil) | `docs/transversais/01-assinatura-eletronica` | planalto.gov.br · gov.br/iti · manual gov.br |
| PNCP / Lei 14.133 (endpoints, prazos art. 94, schemas) | `docs/transversais/02-pncp` | pncp.gov.br/api/…/swagger-ui · planalto.gov.br |
| Acessibilidade (WCAG 2.2, NBR 17225, LBI, eMAG) | `docs/transversais/05-acessibilidade` | w3.org · gov.br/governodigital · ABNT |

Se a pergunta não casar com nenhuma linha, dizer isso e perguntar em qual doc o achado aterrissa — não pesquisar às cegas.

## Passo 2 — Checar o que já existe no repo

Antes de sair pra fonte, `grep`/`Read`: o doc alvo já tem o dado ou uma nota "revalidar"? A fonte já está citada em `docs/09-referencias`? Evita repesquisar o decidido.

## Passo 3 — Pesquisar

- **Pontual** (confirmar 1 artigo, 1 endpoint, 1 número) → `WebFetch`/`WebSearch` direto aqui.
- **Amplo** (comparação multi-fonte, levantamento de mercado/normas) → **não duplicar o fan-out**: recomendar `/deep-research` com a pergunta já refinada pela classificação, citando o doc de destino.

## Passo 4 — Exigir fonte primária/oficial

- Citação legal: só conta o **texto oficial** (planalto.gov.br, in.gov.br, gov.br/anpd, STF, STN). Blog/notícia/resumo = **secundária**.
- Contrato PNCP: só o **Swagger oficial** ou teste direto contra a API — não tutorial de terceiro.
- Se só achou fonte secundária → reportar **"não confirmado"**; nunca promover a citação definitiva.

## Passo 5 — Reportar

Para cada achado:
- **O que foi confirmado** + link + classificação **oficial**/**secundária (não confirmado)**.
- **`doc:§` a atualizar** (e se remove um "revalidar na fonte oficial").
- Recomendação explícita: **"rode `planejar-doc` citando este achado"** — esta skill não edita.

Se nada foi confirmado: reportar claramente, sem inferir valor para preencher a lacuna.

## Regras

- **Nunca** edita — só relata e recomenda.
- **Nunca** inventa citação sem fonte — sem fonte oficial, "não confirmado".
- Sempre distingue primária de secundária — é o que diferencia de uma busca genérica.
- Pergunta ampla/multi-fonte → aponta para `/deep-research`.
