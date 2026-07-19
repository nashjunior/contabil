# Fluxos do sistema

[← Índice](./README.md)

Os diagramas abaixo (Mermaid) renderizam automaticamente no GitHub/GitLab.

- [1. Visão macro](#1-visão-macro)
- [2. Execução da despesa](#2-execução-da-despesa)
- [3. Execução da receita](#3-execução-da-receita)
- [4. Escrituração e correção por estorno](#4-escrituração-e-correção-por-estorno)
- [5. Integração com estruturantes](#5-integração-com-sistemas-estruturantes)
- [6. Acesso e autenticação](#6-acesso-e-autenticação)
- [7. Trilha de auditoria e vedações](#7-trilha-de-auditoria-e-vedações)
- [8. Fechamento de período](#8-fechamento-de-período)
- [9. Transparência em tempo real](#9-transparência-em-tempo-real)
- [10. Consolidação nacional (SICONFI)](#10-consolidação-nacional-siconfi)

## 1. Visão macro

Um fato entra uma única vez; da base única derivam escrituração, transparência e consolidação — sem duplicar dado.

```mermaid
flowchart LR
    subgraph EXT["Sistemas estruturantes (externos)"]
      direction TB
      FP["Folha de pagamento"]
      TR["Arrecadacao / tributario"]
      PT["Patrimonio"]
      LC["Licitacoes / contratos"]
    end
    EXT -->|"integracao automatica (sem intervencao humana)"| ING["Camada de ingestao<br/>APIs - ePING"]
    ING --> CORE
    OP["Operadores (CPF/certificado)"] --> CORE
    subgraph CORE["NUCLEO - Base de dados UNICA"]
      direction TB
      EO["Execucao orcamentaria"]
      FIN["Administracao financeira"]
      CON["Contabilidade (partidas dobradas)"]
      EO <--> FIN
      FIN <--> CON
    end
    CORE --> AUD["Integridade e trilha de auditoria"]
    CORE -->|"tempo real"| TRA["Portal de Transparencia"]
    CORE -->|"extracao"| SIC["SICONFI / orgao central"]
    TRA --> CID["Cidadao"]
    SIC --> TCE["TCE / TCU"]
```

**Decisão:** base contábil única como fonte da verdade — elimina a dupla escrituração *por construção*. `[OBRIGATÓRIO]`

## 2. Execução da despesa

Empenho → liquidação → pagamento (**Lei nº 4.320/1964**). Um **empenho** pode ter **N liquidações** e cada liquidação **N pagamentos** (empenho estimativo/global e parcelamento). Cada estágio gera escrituração automática por partidas dobradas na mesma base.

```mermaid
flowchart TD
    A(["Inicio: demanda de despesa"]) --> B["Verifica dotacao / disponibilidade"]
    B --> C{"Ha credito suficiente?"}
    C -- Nao --> C1["Bloqueia - alerta ao ordenador"]
    C -- Sim --> T{"Tipo de empenho<br/>ordinario / estimativo / global"}
    T --> D["Registra EMPENHO<br/>(classificacao orcamentaria completa)"]
    D --> E["Escrituracao automatica (partidas dobradas)"]
    E --> LP["Aguarda entrega / medicao / conta"]
    LP --> F["Recebe bem/servico + documento de suporte"]
    F --> R{"Saldo do empenho suficiente?"}
    R -- Nao --> RF["REFORCO de empenho<br/>(consome credito da dotacao)"]
    RF --> E
    R -- Sim --> G["Registra LIQUIDACAO (parcial ou total)"]
    G --> E2["Escrituracao automatica"]
    E2 --> H{"Autorizacao do ordenador (alcada)"}
    H -- Nao --> H1["Pendencia - aguarda"]
    H -- Sim --> I["Registra PAGAMENTO (parcial ou total)<br/>(beneficiario CPF/CNPJ, processo)"]
    I --> E3["Escrituracao + baixa financeira"]
    E3 --> J["Publica na transparencia (tempo real)"]
    J --> M{"Ha novas parcelas / entregas?"}
    M -- Sim --> LP
    M -- Nao --> N{"Saldo de empenho a anular?"}
    N -- Sim --> O["ANULACAO de saldo<br/>(devolve credito; original integro)"]
    N -- Nao --> K(["Fim"])
    O --> K
```

**Cardinalidade:** `1 dotação → N empenhos` · `1 empenho → N liquidações` · `1 liquidação → N pagamentos`.

**Travas:** `data de competência segue o fato gerador (Lei 4.320 art. 35), podendo ser retroativa dentro do período aberto; data-hora de registro é o relógio do servidor, imutável (base da trilha); vedado registrar/alterar em período encerrado e vedado alterar o timestamp de registro` · `documento de suporte obrigatório na liquidação` · `beneficiário exigido no pagamento; folha é dispensada de beneficiário linha-a-linha apenas no gate de pagamento consolidado, mas a remuneração individualizada por servidor é exposta na transparência (STF Tema 483)` · `empenho + reforços ≤ crédito da dotação (art. 59)`.

Detalhamento das entidades no [modelo de dados](./10-modelo-dados.md).

## 3. Execução da receita

Previsão → lançamento → arrecadação → recolhimento, com sigilo fiscal preservado na publicação.

```mermaid
flowchart LR
    A(["Previsao orcamentaria (LOA)"]) --> B["Lancamento da receita<br/>(resguardado sigilo fiscal)"]
    B --> C["Arrecadacao (integracao bancaria)"]
    C --> D["Recolhimento / conciliacao"]
    D --> E["Escrituracao automatica (partidas dobradas)"]
    E --> F["Classificacao orcamentaria"]
    F --> G["Publica na transparencia (tempo real)"]
```

**Travas:** `dado sob sigilo fiscal não é exposto individualmente na transparência` · `acesso interno a receita identificada por contribuinte restrito a perfil/escopo específico por necessidade-de-saber, com a leitura registrada na trilha (CTN art. 198 alcança acesso interno, publicação e exportação)`.

## 4. Escrituração e correção por estorno

Erro não se apaga: corrige-se por novo registro, preservando o original íntegro no histórico.

```mermaid
flowchart TD
    A(["Fato contabil"]) --> B["Gera lancamento analitico<br/>debito x credito - valor - historico - data do fato"]
    B --> C["Numeracao sequencial cronologica"]
    C --> D["Grava na base unica (imutavel)"]
    D --> E{"Erro identificado depois?"}
    E -- Nao --> Z(["Consolidado"])
    E -- Sim --> F{"Registro ja consolidado?"}
    F -- Sim --> G["NOVO registro de estorno/retificacao<br/>(original permanece integro)"]
    F -- Nao --> G
    G --> H["Trilha vincula estorno e original"]
    H --> Z
    X["VEDADO: excluir registro"] -. vedado .-> D
    Y["VEDADO: alterar retroativamente"] -. vedado .-> D
```

**Vedações:** `exclusão de registro consolidado` · `alteração retroativa` · `refazer/reprocessar lançamento`.

## 5. Integração com sistemas estruturantes

Comunicação automática, sem redigitação — a base do SIAFIC permanece única.

```mermaid
flowchart LR
    S["Sistema estruturante<br/>(folha/tributos/patrimonio/licitacoes)"] --> Q["Fila / API (ePING)"]
    Q --> V{"Validacao estrutural e de negocio"}
    V -- Invalido --> R["Rejeita - registra erro<br/>- reprocessamento controlado"]
    V -- Valido --> M["Mapeia p/ modelo contabil"]
    M --> W["Grava na base unica"]
    W --> L["Log de integracao<br/>(origem, lote, timestamp)"]
```

**Princípio:** `sem intervenção humana` na comunicação entre sistemas · `[PRODUTO]` monitoramento e reprocessamento de lotes.

**Segurança e integridade da ingestão (serviço de plataforma):** autenticação mútua (mTLS) e/ou token de serviço por origem, allowlist de origens, verificação de assinatura/HMAC do payload ANTES de mapear, e chave de idempotência (origem + tipo + id_evento_externo / hash do lote) com deduplicação exactly-once (inbox) para impedir dupla postagem no razão em reenvio legítimo. Rejeitar por falha de origem/assinatura, não só por invalidez de negócio. A interface é definida agora; a implementação acompanha os conectores. Este é o mecanismo que torna executável a Regra 1 (proibida dupla escrituração).

## 6. Acesso e autenticação

Identidade individual obrigatória; usuários genéricos são vedados.

```mermaid
flowchart TD
    A(["Solicitacao de acesso"]) --> B["Autorizacao da chefia + termo de responsabilidade"]
    B --> C["Cadastro por CPF ou certificado digital"]
    C --> D{"Usuario generico?"}
    D -- Sim --> D1["VEDADO - bloqueado"]
    D -- Nao --> E["Define perfil / alcada (segregacao de funcoes)"]
    E --> F(["Acesso concedido"])
    F --> G["Login: CPF+senha ou certificado - conexao segura (TLS)"]
    G --> H["Toda acao para trilha de auditoria"]
    I["Desligamento / mudanca"] --> J["Revogacao de acesso"]
```

**Travas:** `vedado usuário genérico` · `criptografia em trânsito` · `acesso à base restrito a administradores nominais` · MFA obrigatório para perfis que movimentam recurso (ordenador de despesa, tesouraria/pagamento, administradores de plataforma/banco e acessos privilegiados), aceitando certificado ICP-Brasil ou gov.br Prata/Ouro como fator forte · política de senha forte com hashing moderno e salt (Argon2id/bcrypt/scrypt), limitação de taxa e bloqueio progressivo por conta/IP, timeout de sessão · desprovisionamento com SLA (idealmente automático via evento de RH/folha quando disponível; enquanto não, por termo de desligamento) e recertificação periódica de perfis/alçadas, com relatório de contas órfãs e privilégios sem uso. Registrar tentativas de login falhas na trilha (fluxo 7).

## 7. Trilha de auditoria e vedações

Toda operação é atribuível a uma pessoa e a um instante; tentativas indevidas são bloqueadas e registradas.

```mermaid
flowchart TD
    A(["Operacao (inclusao/alteracao/estorno)"]) --> B{"Passa nas travas?<br/>(backdating, periodo, alcada)"}
    B -- Nao --> C["Bloqueia + registra tentativa"]
    B -- Sim --> D["Executa"]
    C --> E["LOG imutavel: CPF - operacao - data/hora"]
    D --> E
    E --> F["Consulta pesquisavel pelo controle interno"]
    F --> G["Relatorios de excecao (acessos privilegiados, fora de alcada)"]
```

`[OBRIGATÓRIO]` log de autor/ação/timestamp · `[PRODUTO]` relatórios de exceção proativos.

A trilha registra também eventos de **leitura/consulta e exportação** de dados pessoais e sob sigilo (quem, o quê, finalidade, quando, volume), modelados como classe de evento distinta da escrita, gravados no mesmo store imutável segregado e alimentando os relatórios de acesso anômalo. No F0, cobrir ao menos dado sob sigilo fiscal e folha/remuneração; ampliar à demais PII em fase posterior.

## 8. Fechamento de período

Após o encerramento, o sistema impede novos registros com data anterior — correções só por estorno no período aberto.

```mermaid
flowchart LR
    A["Calendario contabil (prazos parametrizaveis - art. 6)"] --> B["Gera balancete de encerramento"]
    B --> C["ENCERRA periodo"]
    C --> D{"Novo lancamento com data no periodo?"}
    D -- Sim --> E["VEDADO - bloqueado"]
    D -- Nao --> F["Aceita no periodo aberto"]
    E --> G["Ajuste apenas por estorno/retificacao"]
```

**Nota:** os prazos (art. 6º) são parametrizáveis e devem seguir a redação vigente do decreto.

## 9. Transparência em tempo real

Do registro à publicação até o 1º dia útil subsequente, com acessibilidade e proteção de dados.

```mermaid
flowchart LR
    A["Registro na base unica"] --> B["Pipeline automatico (SLA de latencia)"]
    B --> C{"Contem dado pessoal protegido?"}
    C -- Sim --> D["Anonimiza / aplica sigilo (LGPD, sigilo fiscal)"]
    C -- Nao --> E["Prepara publicacao"]
    D --> E
    E --> F["Portal (e-MAG acessivel)<br/>despesa - receita - convenios - licitacoes"]
    F --> G["Dados abertos + busca/filtros"]
    G --> H(["Cidadao"])
```

**SLA:** `publicação ≤ 1º dia útil subsequente` · `[OBRIGATÓRIO]` exportação em formato aberto legível por máquina (CSV/JSON) dos dados publicados (LAI art. 8º, §3º; Lei 14.129/2021) · `[PRODUTO]` API pública rica, download de bases completas, dicionário de dados avançado, painéis.

## 10. Consolidação nacional (SICONFI)

Extração da mesma base para consolidação nacional e controle externo, com validação prévia.

```mermaid
flowchart TD
    A["Base de dados unica"] --> B["Extrator parametrizavel (requisitos do orgao central)"]
    B --> C["Gera matrizes / relatorios (RREO, RGF, DCA...)"]
    C --> D{"Validacao previa consistente?"}
    D -- Nao --> E["Aponta inconsistencias - corrige na origem"]
    D -- Sim --> F["Envio ao SICONFI"]
    F --> G["Trilha de geracao/envio"]
    F --> H["Fiscalizacao TCE/TCU"]
```

`[OBRIGATÓRIO]` extração da mesma base · `[PRODUTO]` validação prévia e trilha de envio · `[OBRIGATÓRIO]` calendário legal parametrizável com alertas de vencimento: RREO até 30 dias após cada bimestre (LRF art. 52); RGF até 30 dias após cada quadrimestre — ou semestre para município < 50 mil hab. (LRF art. 55, §2º c/c art. 63); MSC com submissão **mensal** ao SICONFI; DCA anual (prazo STN). O atraso aciona a sanção do art. 23, §3º, I (via art. 73-C).

---

[← Arquitetura](./03-arquitetura.md) · [Índice](./README.md) · [Regras de negócio →](./05-regras-de-negocio.md)
