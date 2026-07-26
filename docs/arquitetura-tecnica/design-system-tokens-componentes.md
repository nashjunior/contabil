# Design system SIAFIC — tokens e componentes-núcleo (F1)

[← Arquitetura técnica](./README.md) · [Fluxo do operador + contrato de API](./fluxo-execucao-operador-contrato-api.md) · [ADR-0026](./adr/0026-design-system-figma-decisoes-estruturais.md) · [04-lgpd](../transversais/04-lgpd.md) · [05-acessibilidade](../transversais/05-acessibilidade.md)

> Design-system-first (RAZ-100): tokens (cor/tipografia/espaçamento/elevação) + 10 componentes-núcleo em Figma, **sem telas** — a biblioteca contra a qual as telas do RAZ-79 nascem depois. Decisões estruturais (terminologia, escopo, tenant do Figma) estão no [ADR-0026](./adr/0026-design-system-figma-decisoes-estruturais.md); este documento é o mapeamento token→uso e o inventário da biblioteca.

**Arquivo Figma:** [SIAFIC — Design System (F1 execução)](https://www.figma.com/design/ObQu8oMQ0cEGbONMXgpuLU) (`fileKey ObQu8oMQ0cEGbONMXgpuLU`, plano "internal projects"). 15 páginas: `Cover`, `Foundations` (cor/tipografia/espaçamento/elevação), e uma página por componente — inclui `10 Alerta / Mensagem Inline (RAZ-144)` (renomeada de `10 Aviso de Gap (API)`; adicionada durante RAZ-102, generalizada durante RAZ-144 — ver linha 11 da tabela abaixo), e `11 Tabela — Balancete (RAZ-112)`, adicionado durante RAZ-112 (consultas/relatórios; ver linha 12). O arquivo também contém duas páginas de telas fora do escopo desta biblioteca: `10 Telas core F1 (RAZ-102)` (operador — empenho/liquidação/pagamento) e `12 Telas de consultas e relatórios (RAZ-112)` (saldo por conta/balancete/execução orçamentária).

---

## 1. Tokens

### 1.1 Primitivos (coleção `Primitives`, 1 modo `Value`, `scopes: []`)

Sóbrio/institucional: `neutral` (12 passos, cinza-azulado), `brand` (9 passos, azul institucional `#2F6CAE`), `success`/`warning`/`danger` (7 passos cada). Ocultos de todo picker — só a camada semântica é exposta a quem monta tela.

**Export DTCG (RAZ-125):** `frontend/tokens/color.tokens.json` (versionado no repo) carrega os passos de `Primitives` alcançáveis pela camada semântica (§1.2) com hex real, confirmado via Figma REST API — ver ADR-0031 (atualização RAZ-125) e `frontend/tokens/README.md` para o método e o resíduo pendente (passos sem alias semântico, que exigem a Variables API ou export via Tokens Studio).

### 1.2 Semânticos (coleção `Color`, 1 modo `Value`)

Sem modo claro/escuro: back-office não pede tema (nenhum doc do produto exige dark mode); um único modo evita escopo não pedido. Se o citizen portal (fora do F1) vier a adotar o **Design System gov.br** ([05-acessibilidade](../transversais/05-acessibilidade.md)), esta camada semântica pode ganhar um segundo modo depois — decisão futura, não deste issue.

| Token | Alias (primitivo) | Uso |
| --- | --- | --- |
| `bg/canvas` | `neutral/50` | Fundo de página/tela |
| `bg/surface` | `neutral/0` | Fundo de card, input, modal, linha de tabela |
| `bg/inset` | `neutral/100` | Cabeçalho de tabela, barra de rodapé, campo desabilitado (base) |
| `bg/disabled` | `neutral/100` | Fundo de campo/estado desabilitado |
| `bg/overlay-scrim` | `neutral/900` | Scrim atrás de modal (uso com opacidade reduzida) |
| `text/primary` | `neutral/900` | Texto principal, valor monetário, título |
| `text/secondary` | `neutral/600` | Labels, legendas, texto de apoio |
| `text/tertiary` | `neutral/500` | Placeholder, hints, captions de documentação |
| `text/disabled` | `neutral/400` | Texto em campo/estado desabilitado |
| `text/on-brand` | `neutral/0` | Texto sobre fundo `brand/*` ou botão primário |
| `text/link` | `brand/600` | Link, item selecionado em menu (Seletor de Ente) |
| `border/default` | `neutral/300` | Borda padrão de input, card, linha de tabela |
| `border/strong` | `neutral/400` | Borda de checkbox/botão secundário, contraste maior |
| `border/focus` | `brand/500` | Anel de foco (teclado) — WCAG 2.2 foco visível |
| `brand/default` | `brand/500` | Botão primário, item ativo, indicador de seleção |
| `brand/hover` | `brand/600` | Estado hover de elemento de marca |
| `brand/pressed` | `brand/700` | Estado pressed de elemento de marca |
| `brand/subtle-bg` | `brand/50` | Fundo de linha selecionada (tabela), item ativo no seletor de ente |
| `state/neutral-{bg,fg,border}` | `neutral/{100,700,300}` | Badge de estágio **Empenhado** |
| `state/info-{bg,fg,border}` | `brand/{50,700,200}` | Badge de estágio **Liquidado** |
| `state/success-{bg,fg,border}` | `success/{50,700,300}` | Badge de estágio **Pago** · badge de aprovação **Aprovado** · banner de sucesso (Resumo Fail-soft) |
| `state/warning-{bg,fg,border}` | `warning/{50,700,300}` | Badge de aprovação **Pendente** · aviso anti-auto-aprovação (Gate 4-eyes) |
| `state/danger-{bg,fg,border}` | `danger/{50,700,300}` | Badge de aprovação **Devolvida** · erro de campo (`saldo_insuficiente`) · lista de rejeitados (Resumo Fail-soft) |
| `pii/mask-bg`, `pii/mask-fg` | `neutral/{100,700}` | Chip de CPF mascarado (estado default) |

**Contraste (WCAG AA, back-office — [05-acessibilidade](../transversais/05-acessibilidade.md)):** todos os pares `*-fg` sobre `*-bg` usados nos badges/chips foram checados pela fórmula de luminância relativa (WCAG 2.x) e ficam ≥ 7:1; `text/on-brand` (branco) sobre `brand/default` fica em ~5.4:1 — passa AA (≥4.5:1) mesmo em texto pequeno. Validação é manual nesta v1 (não há gate automatizado de contraste no Figma); o gate de CI com axe-core é item do F1 de acessibilidade ([05-acessibilidade](../transversais/05-acessibilidade.md)), não deste issue.

### 1.3 Espaçamento e raio (coleções `Spacing`, `Radius`, 1 modo `Value`)

`spacing/2xs..3xl` = 2/4/8/12/16/24/32/48px (`scope: GAP`). `radius/none..full` = 0/4/8/12/999 (`scope: CORNER_RADIUS`).

### 1.4 Tipografia (text styles, família Inter)

| Estilo | Tamanho/altura | Uso |
| --- | --- | --- |
| `Heading/H1` | 24/32 Semi Bold | Título de tela |
| `Heading/H2` | 20/28 Semi Bold | Título de seção (ex.: fila "Pagamentos pendentes") |
| `Heading/H3` | 16/24 Semi Bold | Título de card/modal (Gate de Aprovação, Formulário PCASP) |
| `Body/Default` | 14/20 Regular | Corpo padrão |
| `Body/Strong` | 14/20 Semi Bold | Ênfase em corpo (contagem de seleção, banner de sucesso) |
| `Body/Small` | 12/16 Regular | Texto auxiliar |
| `Label/Default` | 12/16 Medium, tracking 0.4 | Badges, cabeçalho de tabela, label de campo (versaliza no uso) |
| `Money/Default` | 14/20 Regular, tabular, alinhado à direita | Valor monetário em linha/campo |
| `Money/Emphasis` | 16/24 Semi Bold, tabular | Total/destaque (resumo de lote) |

### 1.5 Elevação (effect styles)

`Elevation/Raised` (linha/card) · `Elevation/Overlay` (dropdown do Seletor de Ente) · `Elevation/Modal` (Gate de Aprovação).

---

## 2. Componentes-núcleo (10, um por página)

| # | Componente | Variantes | Ancoragem |
| --- | --- | --- | --- |
| 1 | **Valor Monetário** | `Ênfase=Padrão\|Forte` | ADR-0006 (decimal, nunca float); tabular, alinhado à direita |
| 2 | **Campo de Valor** | `State=Default\|Focus\|Error\|Disabled` | Erro reflete `ErroContrato` do servidor (§6.1 do contrato) sem recalcular no cliente |
| 3 | **CPF Mascarado (PII)** | `Estado=Mascarado\|Integral` | 04-lgpd — máscara `***.456.***-**` é o default; `Integral` exige escopo elevado e é sempre auditado |
| 4 | **Seletor de Ente** | `State=Default\|Open\|Disabled` | ADR-0003 (RLS deny-by-default) — troca de tenant é ação explícita |
| 5 | **Badge Estágio** | `Estágio=Empenhado\|Liquidado\|Pago` | Read model derivado do saldo (§3 do fluxo) — nunca escrito direto |
| 6 | **Badge Aprovação** | `Aprovação=Pendente\|Aprovado\|Devolvida` | Estado forte (ADR-0023) — ver nota de terminologia no ADR-0026 |
| 7 | **Tabela — Seleção em Lote** | `Seleção=Nenhuma\|Parcial` | ADR-0022 — **só existe para pagamento**; linha com beneficiário PF usa o componente CPF Mascarado |
| 8 | **Formulário — Conta PCASP** | `Escriturável=Analítica\|Sintética` | `conta_pcasp` (razao-contabil-schema) — fora do fluxo de operador do F1, ferramental de administração do plano de contas |
| 9 | **Gate de Aprovação (4-eyes)** | `Decisão=Aprovar\|Devolver` | ADR-0023/RAZ-92 — `Devolver` exige motivo; aviso anti-auto-aprovação é só reforço de UX, a checagem real é no servidor |
| 10 | **Resumo Fail-soft** | `Estado=TudoOk\|ParcialComErros` | ADR-0013/ADR-0022 — materializa a resposta `207` de `POST /pagamentos:lote` |
| 11 | **Alerta / Mensagem Inline (Alert)** | `Nível=Crítico\|Atenção\|Informativo\|Sucesso` | Criado como **Aviso de Gap (API)** durante RAZ-102 (só `Crítico`/`Atenção`, para sinalizar lacuna de API na tela — busca em `search_design_system` não retornou nada equivalente). **Generalizado em RAZ-144**: auditoria achou "texto de erro" com estilo inline repetido ad hoc em 4 lugares fora deste componente — a nota anti-auto-aprovação do componente 9 `Gate de Aprovação (4-eyes)` (bakeada como `TEXT` solto em cada uma das 2 variantes `Decisão=Aprovar`/`Decisão=Devolver`) e o `success-banner` do componente 10 `Resumo Fail-soft` (bakeado como `FRAME` solto em cada uma das 2 variantes `Estado=TudoOk`/`Estado=ParcialComErros`). As 4 instâncias ad hoc foram substituídas por instâncias deste componente (`Nível=Atenção` para a nota "REGRA 9"; `Nível=Sucesso` para a confirmação de pagamentos efetivados) — texto do corpo (`Corpo`) passou de largura fixa (400px) para `FILL`, para caber em containers mais estreitos que os 428px nativos do componente sem clipar. Duas variantes novas: `Informativo` (azul, `state/info-*`, alias de `brand/{50,700,200}`) e `Sucesso` (verde, `state/success-*`). **A11y:** `Crítico`/`Atenção` mapeiam para `role="alert"` (região assertiva — erro/risco que exige atenção imediata); `Informativo`/`Sucesso` mapeiam para `role="status"` (região polite — confirmação/contexto, sem interromper o leitor de tela); nenhuma variante depende só de cor, o rótulo (`Título`) em versalete + o corpo carregam o significado. Documentação de uso/a11y como frame próprio na página do componente. Página própria `10 Alerta / Mensagem Inline (RAZ-144)` (renomeada de `10 Aviso de Gap (API)`), entre `09 Resumo Fail-soft` e as telas do RAZ-102 |
| 12 | **Tabela — Balancete** | (sem variantes — colunas fixas) | Adicionado durante RAZ-112 (consultas/relatórios): busca em `search_design_system` mostrou que "Table" na biblioteca "Simple Design System" já anexada é só um **ícone** (40×40), não um componente de dados — nada cobria uma tabela de relatório somente-leitura. Diferente do componente 7 (`Tabela — Seleção em Lote`, específico do lote de pagamento com checkbox), este espelha `BalanceteResponse`/`LinhaBalancete` (RAZ-101) campo a campo — Código/Descrição/Saldo anterior/Mov. Débito/Mov. Crédito/Saldo atual, mais rodapé com totais e o indicador "Confere" (Σdébito=Σcrédito, materializa `Balancete.confere()`), que reusa o componente **`Tag`** da "Simple Design System" (`Scheme=Positive|Danger`, `Removable=false`) em vez de um pill hand-rolled — achado corrigido a partir de feedback do board (RAZ-112: "olha primeiro componentes que podem ser reutilizáveis"; revisão de `search_design_system` por "tag"/"card"/"badge" depois de uma primeira entrega que tinha esse indicador e os cards de estatística hand-rolled). Mesma paleta/tipografia do componente 7 (variáveis de cor reaproveitadas diretamente) para as células/cabeçalho da tabela em si — só o indicador de status virou instância de biblioteca. Página própria `11 Tabela — Balancete (RAZ-112)` |
| — | **Card (Slot)** (Simple Design System, não é componente-núcleo próprio) | — | As telas de consultas (RAZ-112) usam `Card (Slot)` (container com slot vazio, biblioteca "Simple Design System") como wrapper dos cards de resultado (saldo consultado; totais de execução orçamentária) em vez de uma `FRAME` local com `cornerRadius`/`fill` hand-rolled — mesma correção de feedback acima. Não vira linha própria na tabela de componentes-núcleo por ser uma instância direta de biblioteca, sem variantes/campos definidos por este projeto. |

Cobertura (antes de RAZ-112): 91 variáveis (43 primitivas + 35 semânticas + 13 espaçamento/raio), 9 text styles, 3 effect styles, 11 component sets / 27 variantes. RAZ-112 acrescenta 1 componente (`Tabela — Balancete`, sem variantes) — inventário completo não recontado nesta revisão. RAZ-144 não acrescenta componente novo (generaliza o 11º, `Alerta / Mensagem Inline`, de 2 para 4 variantes) e remove 4 duplicações ad hoc (componentes 9 e 10) — nenhum componente novo, nenhuma página nova além da renomeação da página 10.

---

[← Arquitetura técnica](./README.md) · [ADR-0026](./adr/0026-design-system-figma-decisoes-estruturais.md)
