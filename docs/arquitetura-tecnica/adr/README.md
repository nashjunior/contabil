# ADRs — Architecture Decision Records

[← Arquitetura técnica](../README.md) · [Índice geral](../../README.md)

> Cada decisão de arquitetura é **versionada** num arquivo próprio, com **Status** que evolui (Proposta → Aceita → Substituída/Depreciada). Uma decisão nunca se apaga: quando muda, cria-se um novo ADR que **supersede** o anterior (mesmo princípio de imutabilidade do produto). Formato: MADR enxuto.

| ADR | Decisão | Status |
| --- | --- | --- |
| [0001](./0001-base-unica-postgresql.md) | Base contábil única em PostgreSQL relacional | Aceita |
| [0002](./0002-monolito-modular.md) | Monólito modular (não microserviços no MVP) | Aceita |
| [0003](./0003-multi-tenant-rls.md) | Multi-tenant por `tenant_id` + RLS deny-by-default | Aceita |
| [0004](./0004-outbox-idempotente.md) | Publicação via outbox transacional + broker idempotente | Aceita |
| [0005](./0005-trilha-append-only-hash-chain.md) | Trilha append-only com hash-chain em store segregado | Aceita |
| [0006](./0006-dinheiro-decimal.md) | Dinheiro em `NUMERIC`/`BigDecimal` (nunca float) | Aceita |
| [0007](./0007-read-models-cqrs.md) | Read models / CQRS-lite para transparência e relatórios | Aceita |
| [0008](./0008-assinatura-provedor.md) | Assinatura via abstração de provedor (gov.br → ICP-Brasil) | Aceita |
| [0009](./0009-documentos-object-store.md) | Documentos assinados em object store/GED (não BLOB no banco) | Aceita |
| [0010](./0010-single-writer-failover.md) | Single-writer (Postgres primary) com failover fencing | Aceita |
| [0011](./0011-idempotencia-ponta-a-ponta.md) | Idempotência ponta a ponta | Aceita |
| [0012](./0012-stack-jvm.md) | Stack primária = **JVM (Java/Kotlin, Spring Boot)** | Aceita |
| [0013](./0013-persistencia-lote-fail-soft.md) | Persistência em lote com fail-soft e agregação de erros (`toInsert`/`toUpdate`/`toDelete`/`errors`) | Aceita |
| [0014](./0014-contratos-plataforma-ports.md) | Contratos dos serviços de plataforma como ports no `plataforma-domain` (taxonomia de erros estável) | Aceita |
| [0015](./0015-atribuicao-tenant-explicita-no-contrato.md) | Atribuição de tenant explícita no contrato de domínio; campo é `ente` (não `tenantId`) | Aceita |
| [0016](./0016-controle-acesso-mfa-movimentacao-recurso.md) | `ControleAcesso` na application: RBAC + MFA obrigatório para ações que movimentam recurso | Aceita |
| [0017](./0017-bff-oauth-assinatura-govbr.md) | BFF OAuth2 do signatário para Assinatura gov.br | Aceita |
| [0018](./0018-object-store-s3-compativel.md) | Object store S3-compatível (AWS SDK v2 / MinIO), cifrado, referência por URI | Aceita |
| [0020](./0020-f0-tls-backup-imutavel-restauracao.md) | F0: TLS em todas as interfaces, backup cifrado imutável e teste de restauração | Aceita |
| [0021](./0021-contabilizacao-execucao-despesa.md) | Contabilização da execução da despesa: um fato por evento, roteiro no produtor (`execucao`) | Aceita |
| [0022](./0022-lote-pagamento-contrato-api-execucao.md) | Lote é só no pagamento: contrato de API da execução não expõe lote em empenho/liquidação | Aceita |
| [0023](./0023-gate-aprovacao-pagamento-segregacao.md) | Gate de aprovação do pagamento: segundo gate transacional (`APROVAR`), não só papel RBAC | Aceita |
| [0024](./0024-cofre-segredos-f0-env-passthrough.md) | Cofre de segredos F0 via passthrough de ambiente; KMS/HSM gerenciado escala por fase/tier | Aceita |
| [0025](./0025-building-blocks-taticos-ddd.md) | Building blocks táticos de DDD: vocabulário de consulta, sem hierarquia base | Aceita |
| [0026](./0026-design-system-figma-decisoes-estruturais.md) | Design system SIAFIC no Figma: escopo v1, terminologia ("devolvida" vs "rejeitado") e tenant de biblioteca | Aceita |
| [0027](./0027-wiring-empenho-assinatura-gate-interativo.md) | Wiring RegistrarEmpenho → assinatura: gate interativo pós-outbox de documento, não worker autônomo | Aceita |
| [0028](./0028-tipagem-id-fronteira-execucao-razao.md) | Tipagem de id na fronteira execução↔razão: VO de referência local do consumidor, `UUID` contido no adapter, id de domínio não sobe ao shared kernel | Aceita |
| [0029](./0029-contrato-leitura-fila-aprovacao-trilha.md) | Contrato de leitura do gate de aprovação: fila (GET) com segregação Regra 9 imposta na leitura, trilha por endpoint dedicado sobre `AuditoriaLeitura`, `liquidacao_ja_decidida` → 409 | Aceita |
| [0030](./0030-contrato-consultas-razao-convergencia-79.md) | Consultas (RAZ-101) convergem para o contrato RAZ-79 §6.1 (dinheiro string, envelope `{codigo,mensagem,detalhes}`, `mfa`→428, `natureza_saldo` no balancete); balancete é **demonstrativo**, não lista paginável; catálogo PCASP é endpoint novo | Aceita |
| [0031](./0031-frontend-pipeline-design-tokens.md) | Frontend: pipeline de design tokens (RAZ-100 → tema tipado), nomes/escala reais, hex de primitivo pendente de sync | Aceita |
| [0032](./0032-frontend-monolito-modular-feature-based.md) | Frontend: monólito modular (não MFE) + estrutura feature-based (execução/consultas/admin) | Aceita |
| [0033](./0033-frontend-composicao-proibicao-prop-drilling.md) | Frontend: composição (compound components + Context); proibido prop drilling em subcomponente aninhado | Aceita |
| [0034](./0034-frontend-estado-client-api-tipado.md) | Frontend: estado (React Query) e client de API tipado contra os 7 controllers reais (`ente` no path, Bearer gov.br, envelope único) | Aceita |
| [0035](./0035-bff-login-oidc-govbr.md) | BFF de login OIDC gov.br (autenticação geral, não assinatura): `authorization_code`+PKCE server-side no `bootstrap`, asserção guardada no servidor, cookie `HttpOnly` ao SPA, resolvedor stateless com *fallback* aditivo por cookie | Aceita |
| [0036](./0036-design-system-pacote-independente.md) | Design system como pacote npm independente (`@siafic/design-system`) no workspace de frontend: tokens DTCG, pipeline build-tokens, componentes base; app importa via pacote, não contém o DS | Aceita |
| [0037](./0037-isolamento-e-guarda-commits-concorrentes-agentes.md) | Commits concorrentes de agentes: isolamento por worktree/branch, proibição de `git add -A`/`.` e guarda `commit-msg` que bloqueia deleção não reconhecida de arquivo do HEAD (índice git stale) | Aceita |
| [0038](./0038-contrato-api-dotacao-upstream-empenho.md) | Contrato de API da Dotação (upstream do empenho): `GET /entes/{ente}/execucao/dotacoes` com saldo inline (sem N+1 a `/saldo`) + `POST /dotacoes:lote` espelhando `IngerirDotacoes`; estreita RAZ-136 (listagem de dotação sai do F2); sem "saldo bloqueado" enquanto Reserva não for modelada | Aceita |
| [0039](./0039-contrato-leitura-assinatura-empenho-preview-retorno-oauth.md) | Contrato de leitura para assinatura do empenho: `GET /empenhos/{id}` (bloco `documento`) + `GET /empenhos/{id}/documento` (bridge de preview/download sobre `ArmazenamentoDocumentos`) + callback OAuth de assinatura passa a redirecionar (`302`) para rota fixa do SPA, não devolve 204/JSON cru ao navegador do gov.br | Aceita |
| [0040](./0040-verificacao-identidade-workspace-antes-de-done.md) | Verificação de identidade do workspace: resolver `codebase.effectiveLocalFolder` via API de projetos antes de iniciar trabalho, e confirmar o commit no caminho autoritativo antes de declarar `done` — mitiga workspace vazio/cópia desconectada (RAZ-70/58/43/41/86/164/163/166) | Aceita |
| [0041](./0041-frontend-requests-abortaveis-e-camada-de-caso-de-uso.md) | Frontend: `get`/`post` aceitam e repassam `AbortSignal` ao `fetch`, hooks de query encaminham o signal do React Query; lógica de negócio migra para caso de uso puro em `features/*/application/`, nomeado igual ao use case real do backend (sem sufixo `UseCase`); hook fica fino | Aceita |
| [0042](./0042-gate-build-ci-frontend-tsconfig-arquitetura.md) | Gate bloqueante de `npm run build` no job `frontend-test` do CI + `tsconfig.architecture.json` isolado (tipos Node) para o teste de fronteira `application/`, sem afrouxar `tsconfig.app.json` | Aceita |
| [0043](./0043-frontend-forms-react-hook-form-dominio-hexagonal.md) | Frontend: forms em React Hook Form + fronteiras hexagonais — domínio framework-free (VOs/invariantes do Empenho), schema zod derivado do domínio, hook reutilizável `useFormDeAgregado` e wrappers RHF de `FormSection`/`Select` sem mudar o design system | Aceita |
| [0044](./0044-trava-lrf-art42-gate-disponibilidade-por-fonte.md) | Trava LRF art. 42: gate transacional (reusa ADR-0023) que recusa contrair obrigação/inscrever RP sem disponibilidade de caixa **por fonte** nos 2 últimos quadrimestres do mandato; `hard` na janela, monitor fora; base DDR (classes 7/8); `ErroContrato` `disponibilidade_art42_insuficiente` | Aceita |
| [0045](./0045-encerramento-append-only-transicao-condicional.md) | Encerramento de período/exercício por lançamentos de encerramento (append-only, nunca `UPDATE`) + transição `aberto→encerrado` condicional (mesmo padrão anti-corrida do gate de aprovação de pagamento) | Aceita |
| [0046](./0046-segregacao-encerramento-acao-dedicada.md) | Segregação do encerramento: `Acao.ENCERRAR` dedicada (RBAC+MFA) por papel, sem checagem de autor individual (diferente do gate de aprovação de pagamento) | Aceita |
| [0047](./0047-dcasp-via-read-models.md) | Demonstrações DCASP geradas a partir de read models do razão, reusando `GerarBalancete`/`BalancetePort` (ADR-0007) | Aceita |
| [0048](./0048-msc-contrato-unico-siconfi.md) | MSC é o contrato único de prestação de contas ao SICONFI (RREO/RGF/DCA derivam dela, não são gerados); MSC = read model do razão (ADR-0007/0047) | Aceita |
| [0049](./0049-submissao-siconfi-passo-assistido.md) | Submissão ao SICONFI é passo assistido (empacota CSV Anexo II/XBRL GL zipado + upload web com e-CPF A3 ICP-Brasil; sem API M2M de escrita; conciliação via API de consulta read-only) | Aceita |
| [0050](./0050-informacoes-complementares-msc-dimensoes-lancamento.md) | As 9 informações complementares da MSC (PO/FP/DC/FR/CO/NR/ND/FS/AI) são dimensões do lançamento capturadas na escrituração pela origem, não reconstruídas por heurística; MSC é projeção pura | Aceita |
| [0051](./0051-sim-tce-ce-adaptador-remessa.md) | Remessa TCE-CE/SIM é adaptador de saída paralelo, parametrizável por leiaute, desacoplado da MSC (duas saídas independentes sobre o razão) | Aceita |
| [0052](./0052-rbac-dotacao-liquidacao-papeis-fixantes.md) | Matriz RBAC concede `execucao:dotacao` (`CRIAR`/`ALTERAR`) a `ADMIN_PLATAFORMA` e `execucao:liquidacao` (`CRIAR`) a `LANCADOR` — fecha o fluxo do operador ponta a ponta sem misturar "fixa o teto" com "gasta contra o teto" | Aceita |

> **Nota:** o número **0019 não foi utilizado** — reservado durante execução paralela de agentes e nunca materializado em arquivo (o **mesmo** padrão-raiz endereçado pelo [ADR-0037](./0037-isolamento-e-guarda-commits-concorrentes-agentes.md)). Não renumerar os ADRs existentes (a numeração é histórica/imutável, mesmo princípio do ADR); o próximo ADR novo continua a partir do 0052.

## Como adicionar/mudar uma decisão

1. Novo ADR = próximo número, Status **Proposta**.
2. Ao ratificar, muda para **Aceita** (com data).
3. Ao rever, cria-se um **novo** ADR (Status Aceita) que aponta "Supersede ADR-NNNN"; o antigo vira **Substituída** com link para o sucessor. Não se edita a decisão original — versiona-se.

---

[← Arquitetura técnica](../README.md) · [Índice geral](../../README.md)
