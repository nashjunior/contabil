# Base legal

[← Índice](./README.md)

O SIAFIC é o **núcleo** obrigatório, mas em torno dele há uma pilha normativa que qualquer sistema de gestão pública precisa atender — inclusive requisitos **transversais** que derrubam propostas mesmo quando o edital não os cita. A leitura de arquitetura desses transversais está em [Plataforma e requisitos transversais](./11-plataforma-transversal.md).

## Núcleo SIAFIC

| Norma | Papel |
| --- | --- |
| **LRF** (LC 101/2000), art. 48, §1º, III | Exige sistema integrado, padrão mínimo de qualidade e informação em tempo real |
| **LC 131/2009** (Lei da Transparência) | Incluiu o inciso III no art. 48 da LRF |
| **Decreto 10.540/2020** | Estabelece o **padrão mínimo de qualidade** do SIAFIC (o "o quê" obrigatório) |
| **Decreto 11.644/2023** | Plano de ação excepcional e ajustes de prazos (escalonamento até 2025) |
| **LRF, art. 73-C (c/c art. 73-B)** | Sujeita o ente que não implantar o sistema integrado / a transparência (art. 48, §1º e art. 48-A) à sanção do art. 23, §3º, I — impedimento de receber transferências voluntárias |

## Contábil, orçamentário e financeiro

| Norma | Papel |
| --- | --- |
| **Lei 4.320/1964** | Normas gerais de direito financeiro; empenho, liquidação e pagamento (arts. 58–65) |
| **CF, arts. 165–169** | PPA, LDO, LOA e execução orçamentária |
| **PCASP** | Plano de Contas Aplicado ao Setor Público — único e obrigatório para todos os entes |
| **MCASP** (STN, edição vigente) | Manual de Contabilidade Aplicada ao Setor Público — base técnica da escrituração |
| **DCASP / NBC TSP** (CFC) | Demonstrações Contábeis Aplicadas ao Setor Público |

> **Base de obrigatoriedade do plano de contas único:** LRF art. 50, §2º (STN como órgão central de contabilidade), Lei 4.320/1964 art. 85, e a Portaria STN vigente que aprova a edição corrente do PCASP/MCASP.

## Prestação de contas e controle externo

| Norma | Papel |
| --- | --- |
| **SICONFI** (STN) | Envio da DCA, RREO e RGF |
| **Portaria STN 642/2019** | Matriz de Saldos Contábeis (MSC) — formato de envio ao SICONFI; submissão **mensal** |
| **LRF, arts. 52–55** | Relatório Resumido da Execução Orçamentária (RREO) e Relatório de Gestão Fiscal (RGF) |
| **TCE / TCM (ex.: TCE-CE, TCM-CE)** | Remessas aos sistemas do controle externo (SIM, e-Contas) — leiautes variam por ente |

> **Ressalva:** confirmar na fonte oficial (Planalto/STN) o número e o objeto vigentes da Portaria STN da MSC e do Decreto 11.644/2023 (plano de ação/escalonamento de prazos do SIAFIC), pois o leiaute da MSC é atualizado periodicamente pela STN.

## Transversais (aplicam-se a todo o sistema)

Independem do módulo e costumam **derrubar propostas mesmo sem menção expressa no edital**:

| Norma | Exigência |
| --- | --- |
| **Lei 14.133/2021, art. 174** | Publicação de compras, atas e contratos no **PNCP** via API |
| **LC 131/2009 · Decreto 7.185/2010 · LAI (Lei 12.527/2011)** | Transparência em tempo real; portal com dados abertos |
| **LGPD (Lei 13.709/2018)** | Base legal de tratamento, controle de acesso e segurança de dados pessoais |
| **Lei 14.063/2020 · MP 2.200-2/2001 (ICP-Brasil)** | Assinatura eletrônica de empenhos, contratos e documentos oficiais |
| **LBI (Lei 13.146/2015) · eMAG** | Acessibilidade em portais e serviços digitais voltados ao cidadão |

## Escopo: núcleo × estruturantes

O "pacote único" dos editais (contabilidade + licitação + patrimônio + almoxarifado + transparência) é **mais amplo que o SIAFIC**. Juridicamente, o **SIAFIC é só o núcleo** contábil-orçamentário-financeiro + transparência + saída para o SICONFI. **Licitações, patrimônio, almoxarifado, folha e arrecadação são sistemas estruturantes** — ficam *fora* do SIAFIC, mas devem alimentá-lo por integração automática ([Fluxo 5](./04-fluxos.md#5-integração-com-sistemas-estruturantes)). Detalhamento em [Plataforma e requisitos transversais](./11-plataforma-transversal.md).

> **Convenção deste repositório:** requisitos marcados **`[OBRIGATÓRIO]`** derivam diretamente da norma (piso não-negociável); **`[PRODUTO]`** são diferenciais que agregam valor acima do mínimo legal.

Ver também: [Rastreabilidade legal → requisito](./06-rastreabilidade.md) e [Referências normativas](./09-referencias.md).

---

[← Visão e princípios](./01-visao-e-principios.md) · [Índice](./README.md) · [Arquitetura →](./03-arquitetura.md)
