# ADR-0036 · Design system como pacote npm independente (@siafic/design-system)

- **Status:** Aceita
- **Data:** 2026-07-26
- **Contexto:** Os design tokens (ADR-0031) e o componente FormSection estavam embutidos dentro do app (`frontend/src/shared/`). Com a decisão de adotar MFEs (RAZ-119) e de ter o pipeline Figma da Paula (RAZ-100) como fonte única de tokens, o app não pode mais ser o dono dos tokens — ele é apenas um consumidor. Era necessário uma fronteira limpa e um pacote versionável independente do ciclo de release do app.
- **Decisão:** O frontend é organizado como workspace npm com o pacote `@siafic/design-system` em `frontend/packages/design-system`. Esse pacote é a fonte única de tokens DTCG, do pipeline `build-tokens` (Style Dictionary), dos artefatos gerados `theme.{ts,css}` e dos componentes de UI base (ex.: FormSection). O app (`frontend/`) declara `"@siafic/design-system": "*"` como dependência de workspace e importa via `@siafic/design-system` e `@siafic/design-system/theme.css`. O pacote é `private: true` por ora; quando o segundo consumidor (MFE ou app de gestão) for criado, o versionamento semântico é ativado.
- **Consequências:**
  - Bom: fronteira clara — o app depende do DS, não o contém; tokens podem ser atualizados pelo pipeline Figma sem tocar no app; reuso imediato por múltiplos MFEs.
  - Bom: o guardião-frontend pode rejeitar imports hardcoded/locais de tokens de forma rastreável.
  - Ruim: build do workspace requer execução do `ds:build` antes do app; o script `predev`/`prebuild` do app resolve isso automaticamente.
  - Ruim: add de novo componente ao DS requer PR no pacote, não direto no feature; intencionalmente mais formal.
- **Alternativas consideradas:**
  - Manter em `src/shared/` e apenas documentar a convenção — descartado: sem fronteira de pacote, qualquer feature pode importar tokens diretamente e o guardião não tem como distinguir "DS local" de "hardcoded".
  - Publicar o pacote no npm (registry externo) — adiado: overhead desnecessário enquanto há apenas um consumidor; workspace local é suficiente.
  - Monorepo separado para o DS — descartado: agentes e builds partilham o mesmo repo; repo separado exigiria gestão de versões entre repos no ciclo de desenvolvimento.

---

[← ADRs](./README.md)
