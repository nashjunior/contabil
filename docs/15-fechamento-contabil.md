# Fechamento contábil (encerramento de período e de exercício)

[← Índice](./README.md)

> **Fase F1.** Rito que consolida um período contábil e, no mês 13, o exercício:
> apura resultados, encerra contas transitórias, gera as demonstrações e abre o
> exercício seguinte — tudo **append-only**. Os pontos marcados `[REVALIDAR]` dependem
> de confirmação na fonte oficial **MCASP (edição vigente)** antes de produção.

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
  DCASP está confirmada (ver acima). O procedimento de encerramento do exercício segue
  `[REVALIDAR PARCIAL]`: a apuração do resultado **patrimonial** (encerramento de VPA/VPD)
  tem achado de fonte secundária a confirmar na fonte primária; a apuração
  **orçamentária** segue sem fonte encontrada (ver Fontes/a revalidar).
- **Decreto 10.540/2020** — o SIAFIC deve gerar as demonstrações e permitir o encerramento
  com integridade e trilha.

## Preciso (escopo F1)

1. **Encerrar período** (`aberto → encerrado`): setar `status='encerrado'` + `encerrado_em`,
   depois de validar que não há pendência que impeça. O bloqueio de lançamento em período
   fechado **já existe** (trigger `checa_periodo_aberto`, migration V1).
2. **Apuração do resultado patrimonial** (mês 13): lançamentos de encerramento que zeram
   VPA/VPD contra o **superávit/déficit do exercício** (conta de resultado PCASP). `[REVALIDAR]`
   o roteiro exato no MCASP.
3. **Apuração do resultado orçamentário**: encerramento das contas de controle da execução
   (empenhado/liquidado/pago) e do resultado da execução orçamentária.
4. **Inscrição de restos a pagar** no encerramento — *interface* com a frente
   [**Restos a pagar**](./17-restos-a-pagar.md) (a inscrição de RP processados/não-processados
   acontece no rito de encerramento; a trava do art. 42 gateia o que pode ser inscrito por fonte).
5. **Abertura do exercício seguinte**: transposição de saldos das contas patrimoniais
   (permanentes) para o período inicial do novo exercício.
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
        apura resultado orçamentário (contas de controle)
        (interface) inscreve restos a pagar
  -> marca periodo.status = 'encerrado' (condicional, append-only nos fatos)
  -> [exercício] transpõe saldos permanentes p/ novo exercício
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
- `[REVALIDAR PARCIAL]` **Apuração do resultado patrimonial (MCASP/IPC-03)** — achado: as
  contas de VPA (classe 4 do PCASP) e VPD (classe 3) encerram em contrapartida à conta
  **2.3.7.1.0.00.00 — Superávits ou Déficits do Exercício** (patrimônio líquido, saldo por
  1 dia em 31/dez), cujo saldo alimenta a DVP como resultado patrimonial do exercício.
  Fonte: citação de terceiro do texto da IPC-03/STN, não a fonte primária — a extração
  direta do PDF oficial da IPC-03/MCASP falhou nesta pesquisa (ferramenta não decodificou o
  PDF). Serve de base de trabalho para `EncerrarExercicio`, mas exige confirmação do texto
  oficial antes de produção.
- `[REVALIDAR]` **Apuração do resultado orçamentário (MCASP, classes 5/6 do PCASP)** —
  nenhuma fonte concreta encontrada (nem primária nem secundária) sobre o roteiro de
  encerramento das contas de controle da execução (empenhado/liquidado/pago). Lacuna real,
  a resolver antes de implementar essa parte de `EncerrarExercicio`.
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
