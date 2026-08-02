# Fechamento contábil (encerramento de período e de exercício)

[← Índice](./README.md)

> **Fase F1.** Rito que consolida um período contábil e, no mês 13, o exercício:
> apura resultados, encerra contas transitórias, gera as demonstrações e abre o
> exercício seguinte — tudo **append-only**. O roteiro de apuração (patrimonial e
> orçamentário) está confirmado na fonte oficial (**IPC 03/STN**, ver Fontes/a revalidar).

## O que é / base legal

O **fechamento** apura resultados, encerra contas transitórias, gera as demonstrações e
abre o exercício seguinte — sempre por **lançamentos de encerramento** (fatos novos),
nunca por `UPDATE`/`DELETE`.

- **Lei 4.320/1964** arts. 101–106 — demonstrações contábeis obrigatórias: Balanço
  Orçamentário (art. 102, Anexo 12), Balanço Financeiro (art. 103, Anexo 13), Demonstração
  das Variações Patrimoniais/DVP (art. 104, Anexo 14) e Balanço Patrimonial (arts. 105–106,
  Anexo 15) — mapeamento confirmado na fonte oficial (ver Fontes/a revalidar).
- **LRF (LC 101/2000)** — resultado e apuração fiscal do período (alimenta RREO/RGF).
- **MCASP (STN, 11ª edição, dez/2024 — edição vigente)** — a estrutura das 4 demonstrações
  DCASP está confirmada (ver acima). O procedimento de encerramento do exercício —
  apuração do resultado **patrimonial** (VPA/VPD) e **orçamentário** (classes 5/6) —
  está confirmado na **IPC 03/STN** (ver Fontes/a revalidar).
- **Decreto 10.540/2020** — o SIAFIC deve gerar as demonstrações e permitir o encerramento
  com integridade e trilha.

## Preciso (escopo F1)

1. **Encerrar período** (`aberto → encerrado`): setar `status='encerrado'` + `encerrado_em`,
   depois de validar que não há pendência que impeça. O bloqueio de lançamento em período
   fechado **já existe** (trigger `checa_periodo_aberto`, migration V1).
2. **Apuração do resultado patrimonial** (mês 13): lançamentos de encerramento que zeram
   VPA/VPD **diretamente** contra a conta de PL `2.3.7.1.1.01.00` — Superávits ou Déficits
   do Exercício (sem conta transitória intermediária) — roteiro exato confirmado, ver
   Fontes/a revalidar.
3. **Apuração do resultado orçamentário**: encerramento das contas de controle da execução
   (previsão de receita/receita realizada, dotação/crédito disponível/empenhado/liquidado/pago,
   classes 5/6) — roteiro exato confirmado, ver Fontes/a revalidar. **Ordem:** primeiro
   encerra as contas de restos a pagar inscritos no *exercício anterior* (parte do item 4),
   só depois a execução da despesa/receita do exercício corrente — os empenhos não pagos
   do exercício corrente viram os novos restos a pagar do item 4. O encerramento das classes
   5/6 **apenas zera as contas de controle**: **não há conta de "resultado orçamentário" no
   PCASP** — o superávit/déficit orçamentário é **read-model** (Balanço Orçamentário: receita
   realizada − despesa executada, [ADR-0047](./arquitetura-tecnica/adr/0047-dcasp-via-read-models.md)),
   **nunca** um fato de razão. Não criar conta de resultado orçamentário (IPC 03/STN rev. 2017).
4. **Inscrição de restos a pagar** no encerramento — *interface* com a frente
   [**Restos a pagar**](./17-restos-a-pagar.md) (a inscrição de RP processados/não-processados
   acontece no rito de encerramento; a trava do art. 42 gateia o que pode ser inscrito por fonte).
   Inclui o **encerramento da DDR (classes 7/8)**: encerra só a disponibilidade **utilizada**
   (`D 8.2.1.1.4 / C 7.2.1.1.X` por fonte, IPC 03/STN rev. 2017 §§90-96); as comprometidas por
   empenho (`8.2.1.1.2`) e por liquidação (`8.2.1.1.3`) **não** encerram — transitam para o
   exercício seguinte como lastro dos RP por fonte (ver
   [doc 17 §Preciso](./17-restos-a-pagar.md#preciso-escopo-f1)).
5. **Abertura do exercício seguinte**: transposição de saldos das contas patrimoniais
   (permanentes) para o período inicial do novo exercício — incluindo, na DDR, o **superávit
   financeiro por fonte** (`D 8.2.1.1.1.01 / C 8.2.1.1.1.02`, IPC 03/STN rev. 2017 §96), que é a
   base do art. 42 no novo exercício.
6. **Demonstrações DCASP** (Lei 4.320 + MCASP): Balanço Orçamentário, Balanço Financeiro,
   Balanço Patrimonial e DVP, deriváveis do razão por período. Hoje só existe **balancete**
   (`GerarBalancete`).
7. **Trilha + gate**: encerramento é `MOVIMENTA_RECURSO` (RBAC+MFA); evento de auditoria
   dedicado; idealmente **segregação** (quem encerra ≠ quem lança), a decidir em ADR.

## Não preciso (agora)

- Reabertura de período encerrado (exceção rara; se necessário, por rito próprio com trilha
  reforçada — fora do F1).
- Consolidação nacional (SICONFI) e prestação de contas — [frente separada](./16-prestacao-de-contas.md), consome estas demonstrações.
- Ajustes de exercícios anteriores por evento subsequente (tratados por **estorno**, que já
  existe, não por reabertura).

## Como integra (código existente)

- **Já existe:** `periodo_contabil` com `status ∈ {aberto, encerrado}`, `encerrado_em` e
  **mês 13 = encerramento** (V1); `PeriodoContabilPort.periodoAbertoPara`;
  `PeriodoEncerradoException`; estorno append-only (`EstornarFatoContabil`); balancete
  (`GerarBalancete`, `Balancete`). O motor de razão (Σ=Σ, append-only) e a auditoria
  hash-chain servem de base.
- **Falta:** nenhum use case **encerra** o período (o port só *lê* período aberto);
  os lançamentos de encerramento (apuração de resultado); a transposição/abertura; e as
  demonstrações DCASP (só há balancete).

**Novo use case sugerido:** `EncerrarPeriodo` / `EncerrarExercicio` (application, POJO), que
(1) valida RBAC+MFA e ausência de pendências; (2) gera os **lançamentos de encerramento**
(fatos append-only, Σ=Σ) via o razão; (3) marca o período `encerrado` (transição condicional
`WHERE status='aberto'`, mesmo padrão do gate de aprovação de pagamento — evita corrida);
(4) audita. **Nunca** `UPDATE`/`DELETE` de fato/lançamento — o encerramento é mais lançamento,
não mutação.

## Fluxo (alto nível)

```
Operador (com RBAC+MFA) solicita encerrar período/exercício
  -> valida: período aberto, sem fato pendente
  -> [exercício/mês 13] gera lançamentos de encerramento:
        apura resultado patrimonial (VPA/VPD -> superávit/déficit)
        apura resultado orçamentário (contas de controle; sem conta de resultado orçamentário)
        encerra a DDR utilizada por fonte (D 8.2.1.1.4 / C 7.2.1.1.X)
        (interface) inscreve restos a pagar
  -> marca periodo.status = 'encerrado' (condicional, append-only nos fatos)
  -> [exercício] transpõe saldos permanentes p/ novo exercício
        + superávit financeiro por fonte na DDR (D 8.2.1.1.1.01 / C 8.2.1.1.1.02)
  -> trilha de auditoria (evento dedicado)
  -> demonstrações DCASP passam a refletir o período encerrado
```

## Faseamento

- **F1 (go-live):** encerramento de período + apuração de resultado + demonstrações DCASP
  mínimas + inscrição de RP (interface) + abertura do exercício. Pré-requisito de
  [**Restos a pagar**](./17-restos-a-pagar.md) e de [**Prestação de contas**](./16-prestacao-de-contas.md).
- **F2+:** reabertura controlada; consolidação; ajustes avançados.

## Fontes / a revalidar

- **Estrutura das DCASP** — confirmado (Lei 4.320/1964 arts. 101–106 + MCASP 11ª edição,
  STN, dez/2024, Parte V): Balanço Orçamentário = art. 102/Anexo 12; Balanço Financeiro =
  art. 103/Anexo 13; DVP = art. 104/Anexo 14; Balanço Patrimonial = arts. 105–106/Anexo 15.
- **Apuração do resultado patrimonial** — confirmado na fonte primária:
  [**IPC 03/STN — Encerramento de Contas Contábeis no PCASP**](https://cdn.tesouro.gov.br/sistemas-internos/apex/producao/sistemas/thot/arquivos/publicacoes/26305_1605342/anexos/8637_363147/IPC%20Encerramento%20-%20Revis%C3%A3o.pdf)
  (STN, revisão 2017 — vigente; listada em
  gov.br/tesouronacional/pt-br/contabilidade-e-custos/federacao/instrucoes-de-pronunciamentos-contabeis-ipcs),
  itens 19–34 (p. 6–12). As contas de VPD (classe 3) e VPA (classe 4) encerram
  **diretamente** (sem conta transitória) em contrapartida à conta de Patrimônio Líquido
  `2.3.7.1.1.01.00 — Superávits ou Déficits do Exercício` (item 21/24, p. 7/9) — **não**
  `2.3.7.1.0.00.00` (essa é a conta agregadora "Superávits ou Déficits Acumulados", nível
  superior; a IPC 03 desdobra por contexto de consolidação em `2.3.7.1.X.01.00`, X=1 a 5 —
  para o SIAFIC como ente único, o lançamento é sempre contra `2.3.7.1.1.01.00`). A conta
  de resultado tem saldo zero antes do encerramento (item 23) e, após, seu saldo é o
  resultado apurado do exercício (item 26), levado à DVP (item 27). Na abertura do
  exercício seguinte (1º/jan), o saldo é transposto para `2.3.7.1.1.02.00 — Superávits ou
  Déficits de Exercícios Anteriores` (item 30/lançamentos de abertura, p. 11).
- **Apuração do resultado orçamentário (classes 5/6 do PCASP)** — confirmado na mesma
  fonte primária (IPC 03/STN rev. 2017), seção "Encerramento das Contas de Orçamento
  Aprovado e de Restos a Pagar", itens 35–63 (p. 12–26). Cobre: encerramento da Execução
  da Receita (6.2.1.x) contra Previsão Inicial da Receita Bruta (5.2.1.1.1.00.00, item 42);
  encerramento da Execução da Despesa (6.2.2.1.3.0x — crédito disponível/indisponível/
  empenhado a liquidar/em liquidação/liquidado a pagar/liquidado pago) contra Crédito
  Inicial (5.2.2.1.1.01.00) e Dotação Adicional por Tipo de Crédito (5.2.2.1.2.xx), com
  dois roteiros conforme o ente rastreie ou não a origem do crédito desde o empenho
  (itens 44–46); encerramento da Dotação Adicional por Fonte (5.2.2.1.3, controle vertical
  contra 5.2.2.1.3.99.00, item 47); e a ordem obrigatória de encerrar primeiro as contas de
  Restos a Pagar inscritos no **exercício anterior** (item 48) antes da execução da
  despesa do exercício corrente, com a reclassificação RP Não Processados → RP
  Processados quando liquidados e não pagos (itens 49–56). Este roteiro (execução
  orçamentária geral) é anterior e complementar ao já mapeado em
  [**Restos a pagar**](./17-restos-a-pagar.md) (contas de controle de RP/DDR por
  fonte/vinculação, foco na trava art. 42).
- **Encerramento e abertura da DDR (classes 7/8)** — confirmado na mesma fonte primária (IPC 03/STN
  rev. 2017), §§90-96 (p. 45-47) para o encerramento e §§28-33 para os lançamentos de abertura das
  classes 1/2. Encerra **apenas a disponibilidade utilizada** (`D 8.2.1.1.4.00.00 (DDR Utilizada) /
  C 7.2.1.1.X.00.00 (Controle da Disponibilidade de Recursos)`, por fonte/destinação, §91); as
  comprometidas por empenho (`8.2.1.1.2`) e por liquidação (`8.2.1.1.3`) **não** encerram — transitam
  para o exercício seguinte, dando lastro aos RP por fonte. A abertura transpõe o **superávit
  financeiro por fonte** (`D 8.2.1.1.1.01.00 (Recursos Disponíveis para o Exercício) /
  C 8.2.1.1.1.02.00 (Recursos de Exercícios Anteriores)`, §96), base do art. 42 no exercício
  seguinte. Complementa a revalidação do RAZ-232 (que cobriu patrimonial cl.3/4 e orçamentário cl.5/6).
- **Resultado orçamentário não tem conta no PCASP** — o encerramento das classes 5/6 apenas zera as
  contas de controle; o superávit/déficit orçamentário é read-model (Balanço Orçamentário), nunca
  fato de razão (IPC 03/STN rev. 2017; consistente com a estrutura DCASP e o
  [ADR-0047](./arquitetura-tecnica/adr/0047-dcasp-via-read-models.md)).
- Lei 4.320/1964 arts. 101–106 (texto oficial) — planalto.gov.br ficou intermitente nesta
  pesquisa (mesma limitação já registrada no projeto); art. 104 confirmado literal via
  mirror oficial de órgão público e fontes cruzadas; demais artigos pendentes de
  confirmação literal direta contra o Planalto.
- Decreto 10.540/2020 (SIAFIC).

## ADRs registrados

- [**ADR-0045**](./arquitetura-tecnica/adr/0045-encerramento-append-only-transicao-condicional.md)
  — encerramento append-only (lançamentos de encerramento, nunca `UPDATE`) + transição
  `aberto→encerrado` condicional (anti-corrida, mesmo padrão do gate de pagamento).
- [**ADR-0046**](./arquitetura-tecnica/adr/0046-segregacao-encerramento-acao-dedicada.md) —
  segregação do encerramento via `Acao.ENCERRAR` dedicada (RBAC+MFA), sem checagem de autor
  individual (diferente do gate de aprovação de pagamento).
- [**ADR-0047**](./arquitetura-tecnica/adr/0047-dcasp-via-read-models.md) — demonstrações
  DCASP geradas a partir de read models do razão (reusa infra de `GerarBalancete` /
  [ADR-0007](./arquitetura-tecnica/adr/0007-read-models-cqrs.md)).
