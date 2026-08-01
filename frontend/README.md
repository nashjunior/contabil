# SIAFIC — Frontend

React + TypeScript (Vite), organizado como **workspace npm**. Arquitetura: ver
ADR-0031 (tokens), ADR-0032 (estrutura feature-based), ADR-0033 (composição) e
ADR-0036 (design system como pacote independente `@siafic/design-system`) em
`../docs/arquitetura-tecnica/adr/`.

## Rodando

```bash
npm install
npm run dev            # http://localhost:5173 — /entrar (login dev) -> /execucao
npm test               # vitest (unit + componente, contra MSW)
npm run build          # tsc -b && vite build (roda ds:build [tokens do pacote] + api:generate antes)
npm run storybook      # Storybook do design-system, http://localhost:6006 (RAZ-130)
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
    .storybook/   config do Storybook (builder Vite, addon-a11y como gate, addon-docs) — RAZ-130
    tokens/       *.tokens.json (DTCG) — fonte, alimentada pelo pipeline Figma (RAZ-100)
    scripts/      build-tokens.mjs (Style Dictionary) + check-contrast.mjs (gate WCAG AA)
    src/
      tokens/     theme.ts/theme.css — GERADOS (gitignored) pelo pipeline; Tokens.stories.tsx (showcase)
      ui/         compound components do DS (ADR-0033), ex.: FormSection/Select, cada um com .stories.tsx
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
   `dotacaoId` **fechado pela RAZ-199**: `GET /execucao/dotacoes`
   (RAZ-148/ADR-0038) já existia no controller sem client/tela — agora vira o
   combo `DotacaoPicker` (`Select` + `useAsyncOptions`, mesmo padrão do
   `ContaPicker` do razão). `credorId`/`unidadeGestoraId`/`contratoId`
   continuam texto livre (colar UUID) — o backend ainda não expõe consulta de
   credor/unidade gestora (só `ConsultarDotacoes` existe hoje), fora do
   escopo de "1 tela mínima". Nomes e tipos de campo são reais, não
   inventados.
2. **Só empenho tem tela.** Liquidação (com gate de aprovação 4-eyes) e
   pagamento (individual + lote fail-soft) existem no client (`shared/api/client.ts`
   expõe os 3) mas não têm tela própria ainda — próximas issues.
3. **Consulta parcial.** `GET /execucao/orcamentaria` (agregado do período),
   `GET /execucao/dotacoes` (combo do formulário) e `GET /execucao/empenhos`
   (lista da tela, **fechado pela RAZ-199** — RAZ-121 tinha entregue o
   endpoint sem consumidor) estão consumidos. A fila de aprovação (`GET
   /liquidacoes`, paginada/cursor) e a trilha (`GET /liquidacoes/{id}/trilha`)
   existem no controller real mas não estão em nenhuma tela — follow-up.
4. **Login gov.br real — parcialmente fechado pela RAZ-199.** O backend já
   tem um BFF de login real (`SessaoLoginGovBrOAuthController`/ADR-0035,
   RAZ-128): `GET /sessao/oauth/iniciar` conduz o OIDC+PKCE contra o gov.br e
   devolve um cookie de sessão `HttpOnly` (a asserção nunca chega ao
   navegador); `SessaoAutenticadaHttpResolver` já aceita esse cookie como
   fallback aditivo ao `Authorization: Bearer`. `LoginPage` já linka para o
   `/iniciar` real (só em `VITE_API_MODE=real`, já que o endpoint não existe
   para o MSW interceptar) e `shared/api/client.ts` já manda `credentials:
   'include'` + o cabeçalho anti-CSRF (`X-Csrf-Token`) exigido em toda
   chamada mutante autenticada por cookie. **O que falta:** o callback do BFF
   não devolve nenhum corpo com `{cpf, ente, orgao}` — não há hoje um
   endpoint "quem sou eu" para este SPA aprender a claim/ente depois do
   redirect de volta, então `AuthContext` continua sem como hidratar a sessão
   a partir do cookie. `LoginPage`/`AuthContext` seguem sintetizando um
   bearer token opaco local (stand-in de desenvolvimento, não o mecanismo de
   produção) como único caminho para operar as telas neste ambiente. Ver
   comentário em `shared/auth/AuthContext.tsx`; endpoint "quem sou eu" é
   follow-up de backend rastreado separadamente (RAZ-199).
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
7. **(Fechado pela RAZ-199.)** Fronteira de import entre features (ADR-0032)
   agora tem gate real: `oxlint` não expõe um rule set equivalente a
   `eslint-plugin-boundaries`/`import/no-restricted-paths` (mesma lacuna já
   resolvida para a fronteira `application/` sem React, ADR-0041), então o
   gate é um guardrail `vitest` tool-agnóstico —
   `src/architecture/fronteira-import-entre-features.test.ts` — que falha se
   uma feature importar de dentro de outra fora do `index.ts` público, ou se
   `shared/` importar de `features/`.
8. **`react-router-dom@7.18` traz `react-router` com advisory de CSRF em modo
   RSC** (GHSA-qwww-vcr4-c8h2, `npm audit`). Não usamos RSC/server actions
   nesta SPA — não aplicável ao uso atual — mas registrar para não esquecer em
   upgrade futuro. `@redocly/openapi-core` (transitivo de `openapi-typescript`,
   só usado em `npm run api:generate`, nunca no bundle) traz `js-yaml`/
   `brace-expansion` vulneráveis — dev-only, sem fix não-breaking disponível
   ainda.
