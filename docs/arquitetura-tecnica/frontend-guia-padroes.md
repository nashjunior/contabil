# Frontend SIAFIC — guia de padrões (para o Bruno)

[← Arquitetura técnica](./README.md) · [ADR-0031](./adr/0031-frontend-pipeline-design-tokens.md) · [ADR-0032](./adr/0032-frontend-monolito-modular-feature-based.md) · [ADR-0033](./adr/0033-frontend-composicao-proibicao-prop-drilling.md) · [ADR-0034](./adr/0034-frontend-estado-client-api-tipado.md)

Resumo prático das 4 ADRs de frontend (RAZ-119). Leia as ADRs para o "porquê"
— este documento é o "como". Vive em `docs/` (não em `frontend/`) porque a
RAZ-120 está construindo `frontend/` ativamente em paralelo a esta entrega —
ver nota de coordenação no fim.

## As 4 decisões

1. **[ADR-0031](./adr/0031-frontend-pipeline-design-tokens.md) — tokens.** RAZ-100 (Paula) está `done`; nomes/escala reais transcritos de [design-system-tokens-componentes.md](./design-system-tokens-componentes.md) §1 (`bg/canvas`, `text/primary`, `brand/default`, `state/success-{bg,fg,border}`, `pii/mask-{bg,fg}`, spacing `2xs..3xl`=2/4/8/12/16/24/32/48px, radius `none..full`=0/4/8/12/999). Style Dictionary gera tema tipado + CSS vars a partir de `tokens/*.tokens.json`; gate de contraste AA automatizado. Hex exato dos primitivos (exceto `brand/500 = #2F6CAE`, confirmado) está pendente de sync com o Figma real (`fileKey ObQu8oMQ0cEGbONMXgpuLU`, página `Foundations` — a sessão MCP desta entrega só expôs `Cover`). **Nenhum componente referencia cor/spacing bruto** — sempre via tema gerado.
2. **[ADR-0032](./adr/0032-frontend-monolito-modular-feature-based.md) — estrutura.** Monólito modular de front (não MFE agora — mesmo raciocínio do [ADR-0002](./adr/0002-monolito-modular.md) do backend), `features/{execucao,consultas,admin}` com fronteira de import imposta por lint (`import/no-restricted-paths`): uma feature não importa de dentro de outra, só via `index.ts` público; `shared/` nunca importa de `features/`.
3. **[ADR-0033](./adr/0033-frontend-composicao-proibicao-prop-drilling.md) — composição.** Compound components + Context + hooks. Regra explícita: dado que atravessa **3+ níveis** só pra ser repassado (sem uso intermediário) vai por composição (`children`) ou Context — nunca prop furando um componente que não usa o valor. Prop simples continua válida quando o dado não atravessa.
4. **[ADR-0034](./adr/0034-frontend-estado-client-api-tipado.md) — estado + client de API.** React Query pra todo server-state; estado global só para ente ativo/sessão/tema. Client tipado contra os **7 controllers reais** (`EmpenhoController`/`LiquidacaoController`/`PagamentoController`/`RazaoConsultaController`/`ExecucaoConsultaController`/`CatalogoContasController` + assinatura, fora de escopo do client) — não suposição.

## O contrato real (leia antes de qualquer chamada de API)

- **Ente é path segment, não header:** toda rota é `/api/v1/entes/{enteId}/...`. Nome é `ente`/`enteId` — convenção da casa ([ADR-0015](./adr/0015-atribuicao-tenant-explicita-no-contrato.md)), **não** `tenant`/`tenantId`.
- **Auth:** `Authorization: Bearer <asserção gov.br>`, stateless, verificado a cada request (`SessaoAutenticadaHttpResolver`). Sem cookie/sessão para os endpoints de execução/consulta.
- **Dinheiro é sempre string decimal** (`Dinheiro.valor().toPlainString()`) — nunca `number`. Nunca aritmética client-side.
- **Erro é um envelope único** `{codigo, mensagem, detalhes}` ([ADR-0030](./adr/0030-contrato-consultas-razao-convergencia-79.md)). Status por tipo: 401 não-autenticado, 403 sem-permissão, 428 `mfa_requerido`, 409 conflito de saldo/estado/`liquidacao_ja_decidida`/`periodo_encerrado`, 404 `conta_nao_encontrada`, 400 payload inválido.
- **PII (CPF do beneficiário) chega mascarada por padrão** — nunca tente desmascarar no client.
- **Listas paginam por cursor opaco** `{itens, proximoCursor}` (fila de aprovação, catálogo PCASP). **Balancete não pagina** (é demonstrativo, [ADR-0030](./adr/0030-contrato-consultas-razao-convergencia-79.md) §4).
- **Lote de pagamento é fail-soft `207`** (`POST .../pagamentos:lote`) — sempre `{processados, errors}`, nunca lança por item ruim.

### Endpoints (todos sob `/api/v1/entes/{enteId}/`)

| Método | Rota | Controller | Resposta |
| --- | --- | --- | --- |
| POST | `execucao/empenhos` | `EmpenhoController` | `EmpenhoResponse` (201) |
| POST | `execucao/liquidacoes` | `LiquidacaoController` | `LiquidacaoResponse` (201) |
| POST | `execucao/liquidacoes/{id}/aprovacao` | `LiquidacaoController` | `LiquidacaoResponse` |
| POST | `execucao/pagamentos` | `PagamentoController` | `PagamentoResponse` (201) |
| POST | `execucao/pagamentos:lote` | `PagamentoController` | `LotePagamentoResponse` (207) |
| GET | `execucao/orcamentaria?exercicio=&mes=` | `ExecucaoConsultaController` | `ExecucaoOrcamentariaResponse` |
| GET | `execucao/liquidacoes?statusAprovacao=&cursor=&limit=&fonte=&dataInicio=&dataFim=&valorMin=&valorMax=` | `ExecucaoConsultaController` | `FilaAprovacaoResponse` |
| GET | `execucao/liquidacoes/{id}/trilha` | `ExecucaoConsultaController` | `TrilhaResponse` |
| GET | `razao/saldo?contaId=` | `RazaoConsultaController` | `SaldoContaResponse` |
| GET | `razao/balancete?exercicio=&mes=` | `RazaoConsultaController` | `BalanceteResponse` |
| GET | `razao/contas?busca=&cursor=&limit=` | `CatalogoContasController` | `ContasResponse` |

Sem springdoc-openapi no build ainda (`/v3/api-docs` não existe) — os tipos
precisam ser transcritos à mão do `.java` até isso ser adicionado (issue-filha
sinalizada no comentário da RAZ-119, dono: backend). **Não assuma o contrato
pela documentação de produto** (`fluxo-execucao-operador-contrato-api.md`) sem
confirmar contra o `.java` real — foi assim que o [ADR-0030](./adr/0030-contrato-consultas-razao-convergencia-79.md)
encontrou 6 gaps entre doc e código.

## Nota de coordenação (RAZ-119 ↔ RAZ-120)

Esta entrega (RAZ-119) e a RAZ-120 (Bruno, scaffold+auth+client+tela) rodaram
**concorrentemente no mesmo `frontend/` em disco** nesta janela. Houve pelo
menos uma escrita cruzada transiente (`frontend/tokens/README.md` e
`frontend/tsconfig.app.json` foram sobrescritos nos dois sentidos por alguns
segundos) antes de eu parar de tocar `frontend/` inteiramente ao perceber a
concorrência. Se algo em `frontend/` parecer inconsistente ou um build falhar
de forma inexplicável, revise `git diff`/`git status` de `frontend/` com
atenção antes de assumir que é bug de código — pode ser resíduo dessa janela
de corrida. Esta ADR/guia não commitou nada em `frontend/`; só `docs/`.
