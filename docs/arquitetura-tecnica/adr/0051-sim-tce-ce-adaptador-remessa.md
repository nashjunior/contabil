# ADR-0051 · Remessa TCE-CE/SIM é adaptador de saída paralelo, parametrizável por leiaute (desacoplado da MSC)

- **Status:** Aceita
- **Data:** 2026-08-01
- **Contexto:** No Ceará o TCM-CE foi extinto (EC estadual 92/2017; STF ADI 5763) e as contas municipais vão ao **TCE-CE**. A trilha contábil mensal é o **SIM** (Sistema de Informações Municipais): balancetes **mensais por UO/UPC/UG** (IN 01/2025), empacotados via **PGI → ZIP → SIMWEB**. É uma trilha **paralela e independente** da MSC/SICONFI ([ADR-0048](./0048-msc-contrato-unico-siconfi.md)) — nenhuma norma do TCE-CE dispensa uma pela outra (`[REVALIDAR #4]` da spec). O **leiaute interno** do arquivo PGI/SIM ainda é `[REVALIDAR #2]` (o Manual SIM 2026 é PDF escaneado) e o **dia exato do prazo mensal** é `[REVALIDAR #3]`.
- **Decisão:**
  - **O SIM é um adaptador de saída** — port na `application`, adapter na `infra` (padrão do monólito modular, [ADR-0002](./0002-monolito-modular.md)) — que **projeta balancetes do razão** (reusa `GerarBalancete`/read models, [ADR-0007](./0007-read-models-cqrs.md)/[ADR-0047](./0047-dcasp-via-read-models.md)) e os serializa no leiaute do SIM.
  - **O leiaute é parametrizável** (configuração/mapa, não código hard-coded), justamente porque está `[REVALIDAR]` e muda por edição do Manual do SIM. Assim o leiaute pendente **não bloqueia** a decisão de arquitetura: entra como parâmetro quando confirmado.
  - **Não acopla ao gerador de MSC** ([ADR-0048](./0048-msc-contrato-unico-siconfi.md)): são **duas saídas independentes sobre a mesma fonte** (o razão), com granularidades distintas (SIM = balancete por UO/UPC/UG; MSC = agregação por Poder/Órgão + IC). O efeito externo (empacotamento/prazo/agendamento) usa a outbox idempotente ([ADR-0004](./0004-outbox-idempotente.md)/[ADR-0011](./0011-idempotencia-ponta-a-ponta.md)).
- **Consequências:** SIM e MSC evoluem sem interferência; os `[REVALIDAR]` de leiaute (#2) e prazo (#3) entram como parâmetros depois, sem retrabalho de arquitetura. Custo: manter o mapa de leiaute do SIM atualizado a cada edição do manual, e confirmar (`[REVALIDAR #4]`) que SIM e MSC são de fato obrigatórios em paralelo.
- **Alternativas consideradas:**
  - **Derivar a remessa SIM da MSC** — rejeitada: são contratos distintos de normas independentes (TCE-CE ≠ STN); a granularidade UO/UPC/UG do SIM não é a agregação por Poder/Órgão da MSC, e acoplá-los faria uma mudança de uma norma quebrar a outra.
  - **Leiaute do SIM hard-coded no adapter** — rejeitada: o manual muda por ano e o leiaute ainda está `[REVALIDAR]`; um leiaute fixo exigiria recompilar/reimplantar a cada revisão da norma.

---

[← ADRs](./README.md) · [ADR-0002 Monólito modular](./0002-monolito-modular.md) · [ADR-0004 Outbox idempotente](./0004-outbox-idempotente.md) · [ADR-0007 Read models](./0007-read-models-cqrs.md) · [ADR-0048 MSC contrato único](./0048-msc-contrato-unico-siconfi.md) · [Spec docs/16](../../16-prestacao-de-contas.md)
