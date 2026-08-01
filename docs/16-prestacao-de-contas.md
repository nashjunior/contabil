# Prestação de contas (MSC/SICONFI + remessa TCE-CE)

[← Índice](./README.md)

> **Fase F1.** Baseado em pesquisa com fontes primárias (STN/SICONFI, Planalto, TCE-CE, STF).
> Ente-alvo: município do **Ceará**. Os pontos `[REVALIDAR]` dependem de confirmação na fonte
> oficial (e de dados do ente-piloto) antes de produção.

## Insight que organiza tudo

No âmbito federal, a **MSC — Matriz de Saldos Contábeis é a fonte única**. **RREO, RGF e DCA
NÃO são declarados separadamente — são gerados a partir da MSC dentro do SICONFI**
(Portaria STN 642/2019, arts. 9º–10). Logo o contrato primário do produto é **um só: gerar
uma MSC correta**; os demonstrativos fiscais derivam dela. O controle externo do Ceará
(**TCE-CE**, via sistema **SIM**) é uma **trilha paralela e independente**, que coexiste com a MSC.

> Consequência de escopo: **não construir geradores separados de RREO/RGF/DCA no F1** —
> construir o gerador de MSC + o validador local; os demonstrativos saem do SICONFI a partir da MSC.

## O que é / base legal

- **Portaria STN nº 642/2019** — regras de recebimento dos dados contábeis/fiscais no SICONFI
  (base: LRF art. 48 §2º e art. 51). Fundamento de o SIAFIC gerar a MSC: **Decreto 10.540/2020**.
- **LRF (LC 101/2000)** arts. 52–55 (RREO/RGF), art. 51 (consolidação/DCA), art. 63 (opção
  semestral p/ município < 50 mil hab).
- **MDF — Manual de Demonstrativos Fiscais**, **15ª edição** para 2026 (Portaria STN/MF
  2.057/2025, atualizada pela 1.948/2026).
- **Ceará:** o **TCM-CE foi extinto** (EC estadual 92/2017; STF ADI 5763) → contas municipais
  vão ao **TCE-CE**. Trilhas: **SIM** (feed contábil mensal) e **eContas/e-TCE** (PCG/PCS anual;
  IN 01/2025 + Portaria 51/2026).

## Preciso (escopo F1)

1. **Gerador de MSC** — contrato-mãe:
   - **MSC agregada** (mensal): movimentação de todas as contas + info complementares, agregada
     por Poder/órgão. Alimenta RREO/RGF.
   - **MSC de encerramento** (anual, período `YYYY-13`): zera contas de resultado; alimenta a DCA.
   - Cada linha = **conta PCASP Estendido (último nível)** × até 6 informações complementares
     × `TIPO_VALOR` (inicial/movimento/final) × `NATUREZA` (D/C) × `VALOR`.
   - **9 informações complementares** a modelar no razão: **PO** (Poder/Órgão), **FP** (superávit
     financeiro), **DC** (dívida consolidada), **FR** (fonte de recursos), **CO** (execução
     orçamentária), **NR** (natureza da receita), **ND** (natureza da despesa), **FS**
     (função/subfunção), **AI** (ano de inscrição de RP).
   - Classes PCASP: patrimonial (1–4), **controle orçamentário (5–6)**, **DDR (7–8)**.
2. **Validador local pré-envio** espelhando o SICONFI: **Σdébito=Σcrédito por classe**,
   **saldo inicial + movimento = saldo final** por combinação, sem negativos, colunas fixas,
   MSC não-vazia. (O razão já garante Σ=Σ do fato; aqui é a **projeção por classe/IC**.)
3. **Empacotamento + submissão SICONFI**: gerar **CSV (Anexo II) ou XBRL GL**, **zipado**. A
   submissão em si é **upload web + assinatura e-CPF A3 ICP-Brasil** — **não há API pública de
   escrita**. Tratar como **passo assistido/manual**, não integração M2M.
4. **Conciliação via API de consulta** SICONFI (`apidatalake…/ords/siconfi/tt/`, JSON,
   read-only, sem auth): reconciliar o que foi aceito/homologado, monitorar entregas.
5. **Remessa TCE-CE (SIM)** — trilha paralela: balancetes **mensais** por **UO/UPC/UG**
   (IN 01/2025), via **PGI → ZIP → SIMWEB**. Leiaute do Manual do SIM 2026 `[REVALIDAR]`.
6. **Periodicidade parametrizável por população** (art. 63): RREO bimestral / RGF quadrimestral,
   ou semestral se o ente < 50 mil hab.

## Não preciso (F1)

- Geradores próprios de cada anexo RREO/RGF/DCA (derivam da MSC no SICONFI).
- Integração máquina-a-máquina de **envio** (não existe API oficial de escrita).
- eContas/PCG-PCS completo pode ser F1-tardio/F2 (peças PDF assinadas) — priorizar MSC + SIM.

## Como integra (código existente + dependências)

- **Reusa:** razão append-only Σ=Σ, PCASP (catálogo de contas), read models
  ([ADR-0007](./arquitetura-tecnica/adr/0007-read-models-cqrs.md)), `GerarBalancete` como
  base de projeção por período, outbox p/ agendamento por prazo.
- **Depende de:** **[Fechamento contábil](./15-fechamento-contabil.md)** (MSC de encerramento
  precisa dos saldos encerrados) + **Restos a pagar** (dimensão AI e demonstrativo de RP) +
  **Assinatura qualificada ICP-Brasil** (e-CPF A3 exigido para homologar no SICONFI —
  conecta a [transversais/01 · Assinatura](./transversais/01-assinatura-eletronica.md)).

## Prazos (LRF/STN/TCE-CE)

| Entrega | Periodicidade | Prazo |
| --- | --- | --- |
| MSC agregada | mensal | último dia do mês seguinte |
| MSC encerramento | anual | 31/mar |
| DCA | anual | 30/abr (município) |
| RREO | bimestral (ou semestral <50k) | 30 dias após o bimestre |
| RGF | quadrimestral (ou semestral <50k) | 30 dias após o quadrimestre |
| SIM (TCE-CE) | mensal | ~dia 30 do mês seguinte `[REVALIDAR]` |

## Contratos a implementar (priorizados)

1. Gerador de MSC (agregada + encerramento) — CSV Anexo II e/ou XBRL GL, zipado.
2. Validador local espelhando o SICONFI (Σ por classe; inicial+mov=final).
3. Fluxo de submissão assistida SICONFI (zip + assinatura e-CPF A3) — ADR: passo manual/assistido.
4. Conciliação via API de consulta apidatalake (read-only).
5. Adaptador de remessa TCE-CE/SIM (PGI → ZIP → SIMWEB, balancetes por UO/UPC/UG).
6. eContas/e-TCE (PCG/PCS em PDF assinado) — pode faseiar.
7. Parametrização de periodicidade por população (art. 63).

## `[REVALIDAR]` na fonte oficial

1. Numeração/composição exata dos anexos RREO/RGF na **15ª ed. do MDF**.
2. Leiaute interno do arquivo **PGI/SIM** do TCE-CE (XML/base/posicional) — Manual SIM 2026 é PDF escaneado.
3. Dia exato do prazo mensal do SIM.
4. Confirmar que **SIM e MSC são obrigatórias em paralelo** (nenhuma norma do TCE-CE dispensa uma pela outra).
5. DCA: homologação própria vs. 100% derivada da MSC de encerramento.
6. **População do ente-piloto** (fixa a periodicidade RREO/RGF, art. 63).
7. Versão do **Anexo II (leiaute MSC) 2026** (Anexo I verificado byte-a-byte; Anexo II citado, não aberto).

## ADRs a registrar

- **MSC como contrato único** (RREO/RGF/DCA derivam) — evita geradores redundantes.
- **Submissão SICONFI como passo assistido** (sem API de escrita; e-CPF A3 ICP-Brasil).
- **Modelagem das 9 informações complementares** no razão (PO/FP/DC/FR/CO/NR/ND/FS/AI).
- **SIM/TCE-CE como adaptador de remessa** paralelo, parametrizável por leiaute.

## Fontes (primárias)

- Portaria 642/2019 (PDF): `siconfi.tesouro.gov.br/.../PORTARIA_N_642_DE_20_DE_SETEMBRO_DE_2019.pdf`
- Regras Gerais MSC 2026 (Anexo I): `siconfi.tesouro.gov.br/.../2026_Anexo_I_Portaria_STN_642_Regras_Gerais_MSC.pdf`
- MDF (edições/portarias): `gov.br/tesouronacional/pt-br/contabilidade-e-custos/manuais/manual-de-demonstrativos-fiscais-mdf`
- LRF: `planalto.gov.br/ccivil_03/leis/lcp/lcp101.htm`
- API de consulta SICONFI (Swagger): `apidatalake.tesouro.gov.br/docs/siconfi/`
- EC 92/2017 (extinção TCM-CE): `belt.al.ce.gov.br` · STF ADI 5763
- SIM/TCE-CE: `tce.ce.gov.br/sistema-de-informacoes-municipais-sim` · Manual SIM 2026 (PDF)
