# SIAFIC — Frontend

React + TypeScript (Vite), organizado como **workspace npm**. Arquitetura: ver
ADR-0031 (tokens), ADR-0032 (estrutura feature-based), ADR-0033 (composição) e
ADR-0036 (design system como pacote independente `@siafic/design-system`) em
`../docs/arquitetura-tecnica/adr/`.

## Rodando

```bash
npm install
npm run dev      # http://localhost:5173 — /entrar (login dev) -> /execucao
npm test         # vitest (unit + componente, contra MSW)
npm run build    # tsc -b && vite build (roda ds:build [tokens do pacote] + api:generate antes)
```

Não há backend Spring Boot rodável neste ambiente de scaffold (multi-módulo
Gradle + Postgres via Testcontainers/Docker não provisionados) — `npm run dev`
usa MSW (`VITE_API_MODE=mock`, default) contra o mesmo contrato dos testes,
derivado do código real dos controllers (ver `openapi/contrato-provisorio.yaml`).
Para apontar a um backend real: `VITE_API_MODE=real npm run dev`.

## Estrutura

Workspace npm (`workspaces: ["packages/*"]`, ADR-0036). O app é a raiz;
o design system é um **pacote independente** que o app consome via
`@siafic/design-system` — o app depende do DS, não o contém.

```
packages/
  design-system/  @siafic/design-system — FONTE ÚNICA de tokens + componentes do DS
    tokens/       *.tokens.json (DTCG) — fonte, alimentada pelo pipeline Figma (RAZ-100)
    scripts/      build-tokens.mjs (Style Dictionary) + check-contrast.mjs (gate WCAG AA)
    src/
      tokens/     theme.ts/theme.css — GERADOS (gitignored) pelo pipeline
      ui/         compound components do DS (ADR-0033), ex.: FormSection
      index.ts    ponto de entrada público
src/                (o app do operador)
  app/            composition root: providers globais, rotas
  features/
    execucao/     empenho (escrita) + execução orçamentária (consulta) — única feature implementada
  shared/
    api/          client.ts (camada estável) + generated/ (GERADO) + mocks/ (MSW)
    auth/         sessão gov.br (bearer) + ente ativo (dev-only — ver gap)
    lib/          Dinheiro (decimal), CPF (PII mascarada), moneyJson (parse seguro)
```

Tokens e componentes do DS **não** vivem mais em `src/shared/` — o
`guardiao-frontend` exige que venham do pacote `@siafic/design-system`
(não hardcoded nem local ao app).

## Nota sobre este scaffold: correção de contrato feita durante a construção

A primeira versão deste client foi escrita contra um `ExecucaoController`
consolidado (3 POST, headers `X-Govbr-*` customizados) encontrado numa réplica
de workspace de agente que estava **desconectada do repositório real**. Ao
localizar o repositório real durante esta mesma sessão, o contrato mostrou ser
bem mais rico e **diferente**: `EmpenhoController`/`LiquidacaoController`/
`PagamentoController` separados (RAZ-105) + `ExecucaoConsultaController`
(RAZ-101/ADR-0029) real, com `enteId` no **path** (não header) e autenticação
por um único `Authorization: Bearer <asserção gov.br>` verificado a cada
request (não os 3 headers customizados assumidos antes). O client, o contrato
OpenAPI e as telas foram **reescritos** para bater com o código real antes
desta entrega ser considerada pronta — ver `openapi/contrato-provisorio.yaml`
para a proveniência exata (arquivo por arquivo) de cada campo.

## Gaps sinalizados (RAZ-120) — ler antes de estender

1. **Escopo de campos do Empenho.** O `EmpenhoRequest` real tem `dotacaoId`/
   `credorId`/`unidadeGestoraId`/`contratoId` (UUIDs de cadastros próprios).
   A tela mínima aceita esses IDs como texto livre (colar UUID) — uma tela
   completa teria autocomplete/picker por cadastro, fora do escopo de
   "1 tela mínima". Nomes e tipos de campo são reais, não inventados.
2. **Só empenho tem tela.** Liquidação (com gate de aprovação 4-eyes) e
   pagamento (individual + lote fail-soft) existem no client (`shared/api/client.ts`
   expõe os 3) mas não têm tela própria ainda — próximas issues.
3. **Consulta parcial.** Só `GET /execucao/orcamentaria` (agregado do
   período) está consumido. A fila de aprovação (`GET /liquidacoes`,
   paginada/cursor) e a trilha (`GET /liquidacoes/{id}/trilha`) existem no
   controller real mas não estão nesta tela — follow-up.
4. **Sem login gov.br real.** O backend verifica a *forma* de um bearer
   token (`Authorization: Bearer <asserção>`) a cada request
   (`SessaoAutenticadaHttpResolver`/`ServicoIdentidade.autenticar`), mas
   obter uma asserção gov.br real exige registro de cliente OAuth2/OIDC junto
   ao gov.br (processo externo, credenciais de governo — análogo ao runbook
   de assinatura, mas para login geral). `DevLoginPage`/`AuthContext`
   sintetizam um bearer token opaco local — stand-in de desenvolvimento, não
   o mecanismo de produção. Ver comentário em `shared/auth/AuthContext.tsx`.
5. **(Fechado majoritariamente pela RAZ-125.)** `packages/design-system/tokens/color.tokens.json` já
   era a nomenclatura semântica real (`bg.*`/`text.*`/`brand.*`/`state.*`/
   `pii.*`); agora todo valor hex também é real (lido via Figma REST API da
   página `Foundations`, resolvendo cada swatch semântico e revertendo pelo
   alias documentado em §1.2 do doc de design system), exceto `neutral.200`
   (inferido por continuidade da paleta, sem alias semântico para confirmar).
   Resíduo: os passos do `Primitives` sem consumidor semântico (a maioria de
   `brand`/`success`/`warning`/`danger`) exigem a Figma Variables REST API
   (bloqueada por escopo no token atual) ou export manual via Tokens Studio —
   não bloqueiam nenhum componente, já que primitivos ficam ocultos da camada
   semântica (ADR-0026). Ver `packages/design-system/tokens/README.md` e
   ADR-0031 (atualização RAZ-125).
6. **Sem backend rodável neste ambiente para prova 100% real.** A prova
   fim-a-fim aqui é: client real (paths/campos/auth batendo com o código-fonte
   lido diretamente) + MSW fiel ao mesmo contrato + teste de componente
   cobrindo login → registrar empenho → GET real de agregado refletindo o
   valor (`features/execucao/__tests__/fluxo-execucao.test.tsx`).
7. **Fronteiras de feature não têm gate de lint/CI ainda** — só `oxlint`
   está configurado; `eslint-plugin-boundaries`/regra equivalente para impedir
   import cruzado entre `features/*` fica como follow-up.
8. **`react-router-dom@7.18` traz `react-router` com advisory de CSRF em modo
   RSC** (GHSA-qwww-vcr4-c8h2, `npm audit`). Não usamos RSC/server actions
   nesta SPA — não aplicável ao uso atual — mas registrar para não esquecer em
   upgrade futuro. `@redocly/openapi-core` (transitivo de `openapi-typescript`,
   só usado em `npm run api:generate`, nunca no bundle) traz `js-yaml`/
   `brace-expansion` vulneráveis — dev-only, sem fix não-breaking disponível
   ainda.
