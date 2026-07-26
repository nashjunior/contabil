# Design tokens (ADR-0031)

Estes 4 arquivos (`color`, `spacing`, `typography`, `radius`) são um seed **parcialmente
real**: RAZ-100 (Paula) **entregou** o design system (biblioteca Figma `ObQu8oMQ0cEGbONMXgpuLU`,
15 páginas, ver `docs/arquitetura-tecnica/design-system-tokens-componentes.md`), mas não
como export DTCG/JSON versionado — só Figma + a doc markdown. A nomenclatura semântica
abaixo (`bg.*`, `text.*`, `border.*`, `brand.*`, `state.*`, `pii.*`) e a escala de
spacing/radius (`2xs..3xl`, `none..full`) **são reais**, extraídas dessa doc. Os valores
hex são reais só onde a doc documenta em texto (`brand.500 = #2F6CAE` — ver
`$description` no token); os demais passos de `neutral`/`brand`/`success`/`warning`/
`danger` são placeholder (a sessão Figma MCP disponível nesta execução só expunha a
página `Cover`, não `Foundations`, mesma limitação registrada em ADR-0031/0033 do
repo real). **Trocar por export completo quando disponível, mantendo a mesma forma
DTCG e os mesmos nomes de token** — nenhum componente muda, pois nenhum componente
referencia valor bruto (todos consomem `theme.<categoria>.<token>` ou a variável CSS
equivalente, geradas por `npm run tokens:build`).

Formato: [Design Tokens Community Group (DTCG)](https://tr.designtokens.org/format/) —
`$type`/`$value` por token, aninhado por categoria.

`color.tokens.json` também carrega `color.$extensions.contabil.contrastPairs` —
pares foreground/background que o build valida contra WCAG AA (4.5:1 texto normal,
badges/chips). Gate automatizado do ADR-0031 item 4: `npm run tokens:build` falha
(exit 1) se um par cair abaixo do mínimo declarado.

Saída gerada (não editar à mão, regenerada por `npm run tokens:build`):
- `../src/shared/tokens/theme.ts` — tema tipado (`as const` + `Theme`).
- `../src/shared/tokens/theme.css` — custom properties CSS.
