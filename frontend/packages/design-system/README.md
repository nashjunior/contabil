# @siafic/design-system

Pacote independente (ADR-0036) com os tokens e componentes do design system do
SIAFIC. O app (`frontend/`) consome via `@siafic/design-system` — não contém o
DS, depende dele.

```
tokens/       *.tokens.json (DTCG) — fonte, alimentada pelo pipeline Figma (RAZ-100)
scripts/      build-tokens.mjs (Style Dictionary) + check-contrast.mjs (gate WCAG AA)
src/
  tokens/     theme.ts/theme.css — GERADOS (gitignored) pelo pipeline
  ui/         compound components (ADR-0033), cada um com .stories.tsx ao lado
```

## Storybook (RAZ-130)

```bash
npm run storybook          # dev server, http://localhost:6006
npm run build-storybook    # build estático em storybook-static/ (gitignored)
```

Builder Vite (`@storybook/react-vite`), com `@storybook/addon-a11y` e
`@storybook/addon-docs`. O `preview.tsx` carrega `src/tokens/theme.css` (as
stories renderizam com os tokens reais, não estilo do browser) e define
`parameters.a11y.test = 'error'` — violação de acessibilidade **quebra** a
story (erro, não warning), consistente com o gate que o `guardiao-frontend`
exige (eMAG/WCAG AA).

**Convenção:** todo componente do DS entrega `<Componente>.stories.tsx`
colocado ao lado do componente (ver `ui/FormSection/` e `ui/Select/`). Um
componente novo sem story é um gap a sinalizar — mesmo espírito de um
componente sem teste.

`Foundations/Tokens` (`src/tokens/Tokens.stories.tsx`) é a página de showcase
dos tokens semânticos (cores, espaçamento, raio, tipografia), lida
diretamente do `theme` gerado — nunca hardcoded.
