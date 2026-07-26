# Design tokens (ADR-0031)

Estes 4 arquivos (`color`, `spacing`, `typography`, `radius`) espelham o design system
Figma `ObQu8oMQ0cEGbONMXgpuLU` (RAZ-100, ver
`docs/arquitetura-tecnica/design-system-tokens-componentes.md`). A nomenclatura semântica
(`bg.*`, `text.*`, `border.*`, `brand.*`, `state.*`, `pii.*`) e a escala de spacing/radius
(`2xs..3xl`, `none..full`) são reais, extraídas dessa doc.

**Cor (RAZ-125, atualizado):** todo valor hex atualmente presente em `color.tokens.json`
é **REAL**, exceto `neutral.200` — confirmado lendo, via Figma REST API
(`GET /v1/files/ObQu8oMQ0cEGbONMXgpuLU/nodes?ids=2:50`, autenticada com o token já
presente em `.mcp.json`, escopo `file_content:read`), o fill renderizado de cada swatch
semântico da página `Foundations` e revertendo para o primitivo pelo alias documentado
em design-system-tokens-componentes.md §1.2 (ex.: `bg/canvas` resolve para `#f8fafc` e é
alias de `neutral/50`). Todo par fg/bg do gate de contraste foi revalidado com os novos
valores (`npm run tokens:build` — ver abaixo) e continua passando WCAG AA. Ver
`$description` por token para a evidência exata (variável Figma de origem).

**Gap restante:** a coleção `Primitives` completa do Figma (`neutral` 12 passos, `brand`
9, `success`/`warning`/`danger` 7 cada — ADR-0026/§1.1) tem passos que **nenhum token
semântico referencia**, então não aparecem em nenhum fill renderizado na página
`Foundations` e não podem ser lidos por essa via. A Figma Variables REST API
(`GET /v1/files/:key/variables/local`, que exporia a coleção `Primitives` diretamente)
retorna 403 com o token atual — falta o escopo `file_variables:read`
(`{"message":"... requer o escopo file_variables:read"}`). Fechar esse resíduo exige:
(a) regenerar o personal access token do Figma com esse escopo (se o plano "internal
projects" permitir — API de Variables é normalmente Enterprise-only) e repetir a
chamada, ou (b) export manual via plugin Tokens Studio por alguém com acesso de edição
ao arquivo. `neutral.200` foi a única exceção onde um valor foi preenchido sem essa
confirmação: os outros 9 passos de `neutral` bateram exatamente com a paleta `slate` do
Tailwind CSS, então foi inferido por continuidade da mesma progressão — ver
`$description` do token, que sinaliza isso como não confirmado. `brand`/`success`/
`warning`/`danger` não batem com nenhuma paleta conhecida, então seus passos sem alias
semântico simplesmente não foram preenchidos (arquivo fica menor que 12/9/7/7/7, não
com valores inventados). Nenhum componente do frontend referencia esses passos —
primitivos ficam ocultos da camada semântica por decisão do ADR-0026, então este
resíduo não bloqueia nada em uso atual.

Formato: [Design Tokens Community Group (DTCG)](https://tr.designtokens.org/format/) —
`$type`/`$value` por token, aninhado por categoria.

`color.tokens.json` também carrega `color.$extensions.contabil.contrastPairs` —
pares foreground/background que o build valida contra WCAG AA (4.5:1 texto normal,
badges/chips). Gate automatizado do ADR-0031 item 4: `npm run tokens:build` falha
(exit 1) se um par cair abaixo do mínimo declarado.

Saída gerada (não editar à mão, regenerada por `npm run tokens:build`):
- `../src/shared/tokens/theme.ts` — tema tipado (`as const` + `Theme`).
- `../src/shared/tokens/theme.css` — custom properties CSS.
