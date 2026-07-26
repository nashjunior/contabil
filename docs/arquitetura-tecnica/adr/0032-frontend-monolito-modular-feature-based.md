# ADR-0032 · Frontend: monólito modular (não MFE) + estrutura feature-based

- **Status:** Aceita
- **Data:** 2026-07-26
- **Contexto:** RAZ-119 pede avaliar MFE vs monólito modular de front pelas fronteiras reais: escrita da execução (empenho/liquidação/pagamento/aprovação — `EmpenhoController`/`LiquidacaoController`/`PagamentoController`), consultas (saldo/balancete/execução orçamentária/catálogo PCASP/fila de aprovação/trilha — `RazaoConsultaController`/`ExecucaoConsultaController`/`CatalogoContasController`) e admin (fora do escopo do F1 hoje — sem controller). Mesmo raciocínio do [ADR-0002](./0002-monolito-modular.md) (backend): microserviços/MFE só valem quando o custo de coordenação distribuída é menor que o ganho de desacoplamento — não é o caso agora.
- **Decisão:** **Monólito modular de frontend** — um único deployable (SPA), organizado em `features/` com fronteiras de import impostas por lint (`import/no-restricted-paths`), não por deploy separado.

  ```
  frontend/src/
    app/                 # composition root: router, providers, shell/layout
    features/
      execucao/          # empenho, liquidação, pagamento, aprovação (escrita)
      consultas/         # saldo, balancete, execução orçamentária, catálogo PCASP
      admin/             # sem controller ainda — placeholder de fronteira
    shared/
      ui/                # compound components genéricos (ADR-0033)
      tokens/            # tema tipado gerado (ADR-0031)
      api/                # client HTTP base (ente no path, Bearer gov.br), tipos
      hooks/, lib/        # Dinheiro (decimal), CPF mascarado, formatação
  ```

  Regras: uma feature não importa de dentro de outra feature (só via `index.ts` público); `shared/` não importa de `features/`; só `app/` conhece mais de uma feature. Nenhuma infra de module federation agora.

  **Por que não MFE agora:** (1) um único engenheiro de front (Bruno) hoje — sem paralelismo de time a desacoplar; (2) backend é monólito único (ADR-0002) — cadências de deploy divergentes por fronteira de front não têm contrapartida no backend; (3) module federation custa complexidade de build/CI/depuração paga **antecipadamente**, com retorno só quando times/deploys realmente divergirem.

  **Costura para MFE futuro:** a fronteira `features/<nome>/index.ts` (API pública) já isolada é o ponto de corte se a necessidade aparecer — reavaliado em ADR própria quando o gatilho existir (time dedicado com deploy próprio, ou embutir uma fronteira como widget externo).
- **Consequências:**
  - **+** Um único build/deploy; fronteiras de feature verificáveis em lint (CI), não só convenção.
  - **+** Caminho evolutivo para MFE fica pronto sem ser pago agora.
  - **−** Se o time crescer e precisar de paralelismo real por fronteira, extrair é trabalho + nova ADR — aceito, mesmo trade-off do ADR-0002.
  - **−** Exige disciplina de lint para a fronteira não virar só uma pasta sem imposição real.
- **Alternativas consideradas:** Module federation desde já, alinhado 1:1 com escrita/consultas/admin (rejeitada — nenhum dos critérios que pagam o custo de MFE está presente; ver Decisão); um único diretório `src/` sem fronteira de feature (rejeitada — perde a rastreabilidade das 3 fronteiras reais que a issue pede para justificar, mesmo dentro de um monólito).

---

[← ADRs](./README.md) · [ADR-0002](./0002-monolito-modular.md)
