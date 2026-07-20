# ADR-0020 — F0: TLS em todas as interfaces, backup cifrado imutável e teste de restauração

- **Status:** Aceita
- **Data:** 2026-07-19
- **Contexto relacionado:** RAZ-7 (piso de segurança F0), RAZ-36 (esta trilha IaC),
  ADR-0002 (monólito modular / base contábil única), ADR-0012 (plataforma JVM,
  Flyway dono do schema), Decreto 10.540/2020 (SIAFIC), LGPD (Lei 13.709/2018).

## Contexto

O piso de segurança **F0** (RAZ-7) não se esgota no código Java. O Decreto
10.540/2020 (SIAFIC) exige, para o sistema único, **integridade, disponibilidade e
rastreabilidade** dos dados contábeis, incluindo transporte protegido e cópias de
segurança que permitam **reconstituição** da base. A LGPD reforça confidencialidade
e integridade dos dados pessoais em trânsito e em repouso.

Até aqui o repositório só carregava artefatos de aplicação (Gradle/Spring) e as
migrações Flyway. Faltava a camada operacional que qualquer ente precisa operar em
produção e que uma auditoria TCE/ANPD cobra como evidência:

1. **TLS** em todas as interfaces expostas — não só na borda pública.
2. **Backup cifrado** com **imutabilidade** (ou cópia air-gapped) — um backup que
   pode ser sobrescrito ou apagado por quem comprometeu o ambiente não protege
   contra ransomware nem contra adulteração.
3. **Prova de restauração** — gerar backup não é evidência de que ele restaura.
   Sem teste periódico de restauração, o RPO/RTO é presumido, não medido.
4. **RPO/RTO por ente/contrato** — o alvo de perda e de indisponibilidade é
   contratual e varia por porte de ente; não pode ser um número fixo no código.

Sem esta trilha o RAZ-7 (F0) fica incompleto: há controle de aplicação sem piso
operacional que o sustente.

## Decisão

### 1. TLS obrigatório em **todas** as interfaces expostas (piso TLS 1.2, preferência 1.3)

Nenhum tráfego em claro entre componentes. A matriz de interfaces e o alvo:

| Interface | Exposição | Terminação TLS |
| --- | --- | --- |
| Cliente → sistema (HTTP) | pública | proxy reverso na borda (`infra/tls/nginx-razao.conf.exemplo`) **ou** `server.ssl` do Spring; HSTS; redirect 80→443 |
| App → PostgreSQL (JDBC) | interna | `sslmode=verify-full` + `sslrootcert` (CA); Postgres `ssl=on` |
| App → destinos de publicação (Transparência, PNCP, SICONFI) | saída | HTTPS obrigatório; sem downgrade |
| Endpoints de gestão (actuator) | interna | porta separada, TLS, bind restrito à rede de operação |

Cifras e protocolos ficam num piso explícito (TLS 1.2/1.3, suítes AEAD). Certificados
e chaves privadas **nunca** no repositório — vêm do cofre/da esteira de deploy.

### 2. Backup **cifrado** com imutabilidade ou cópia air-gapped

Duas trilhas complementares, ambas com cifra AES-256 e chave **exclusivamente do
cofre** (invariante "segredos no cofre"):

- **Trilha A — física/PITR (padrão quando há object storage):** pgBackRest, repo
  cifrado, em **object storage S3-compatível com Object Lock (WORM, modo
  compliance)**. A imutabilidade é do provedor de armazenamento: nem o operador nem
  uma credencial comprometida apagam ou sobrescrevem o backup dentro da janela de
  retenção. WAL archiving habilita PITR e derruba o RPO.
- **Trilha B — lógica air-gapped (padrão em ente sem object storage):** `pg_dump`
  em formato custom, cifrado (`age`), exportado para **mídia offline/air-gapped**.
  A cópia só entra no cofre físico **após** a restauração de verificação passar.

O ente escolhe a trilha conforme o ambiente-alvo; ambas satisfazem o F0. A chave de
cifra nunca é versionada nem gravada em disco em claro.

### 3. **Prova de restauração** periódica, não só geração de backup

`infra/restore/restore-drill.sh` restaura o último backup em uma **instância
scratch descartável**, roda verificações de integridade e **mede o RTO**, emitindo
um **relatório de evidência** datado (id do backup, RPO observado, RTO medido,
resultado de cada verificação, veredito). O drill **falha** (exit ≠ 0, evidência
`REPROVADO`) se qualquer verificação reprovar ou se RTO/RPO medidos violarem o alvo
do ente. As verificações ancoram-se no schema **real** das migrações `V1`/`V2` —
tudo em `public`, chave de isolamento `ente_id` (ADR-0015); nenhuma migração cria
schema `contabil`:

- **Migrações** — `flyway_schema_history` sem falhas; a base restaurada está na
  versão esperada.
- **Trilha de auditoria append-only** — `auditoria_evento`: sequência **contígua a
  partir de 1 por ente** e `hash_anterior` **encadeado** ao `hash_evento` do
  evento anterior. Prova que o backup preservou a cadeia de hash imutável — não uma
  cópia parcial ou corrompida.
- **Integridade referencial** — ausência de eventos de auditoria órfãos de `ente`.
  (O oráculo NÃO referencia tabela `outbox`: nenhuma migração a cria — cf. RAZ-50; a
  verificação será reintroduzida, gated por `to_regclass`, quando o outbox
  transacional entrar.)
- **Razão de partidas dobradas (Σdébito = Σcrédito)** — gancho
  (`infra/restore/verificacoes-razao.sql`) **ativo**: `V1` já cria `lancamento`, então
  a verificação roda (probe `to_regclass('lancamento')`), verificando Σd=Σc por fato e
  o balanço global. Fica inerte apenas se a tabela não existir na base restaurada.

A frequência do drill é parametrizável (ver item 4). A evidência é o artefato que a
auditoria TCE/ANPD consome.

### 4. RPO/RTO e política de retenção **parametrizáveis por ente/contrato**

Nenhum número de RPO/RTO/retenção/janela de imutabilidade fica no código. Os
scripts leem de um arquivo de parâmetros **por ente** (`infra/params/<ente>.env`, a
partir de `infra/params/exemplo.env.sample`) e **abortam se a variável não estiver
definida** (`: "${RPO_ALVO_MIN:?...}"`) — não há default embutido que possa vazar
para produção como valor "de fábrica".

## Racional

- **Imutabilidade > só cifra:** cifrar o backup protege confidencialidade, mas não
  impede que um atacante com credencial o apague. Object Lock/WORM ou air-gap é o
  que protege **disponibilidade e integridade** do backup — o cerne do F0.
- **Restauração medida > presumida:** o par RPO/RTO só é verdadeiro se restaurar de
  fato. A verificação da cadeia de hash de `auditoria_evento` transforma o drill de
  "o `pg_restore` retornou 0" em "a base restaurada preserva a invariante
  append-only" — prova de integridade contábil, não só de bytes.
- **Parametrizar por ente:** o SIAFIC é multi-ente (multi-tenant, ADR-0003). O alvo
  de perda/indisponibilidade é contratual; fixá-lo no código seria impor o número de
  um ente a todos e violaria o requisito de auditoria.
- **Fora do domínio Java:** TLS/backup/restauração são infraestrutura. Vivem em
  `infra/` e `docs/operacao/`, não nos módulos `*-domain/application/infra` do
  monólito — coerente com a fronteira do ADR-0002.

## Consequências

- **+** F0 (RAZ-7) ganha piso operacional auditável: TLS ponta a ponta, backup
  imutável/air-gapped e evidência de restauração periódica com RPO/RTO medidos.
- **+** A prova de restauração reusa as invariantes do próprio sistema (append-only,
  hash-chain) como oráculo de integridade — o drill fica mais forte conforme o
  domínio cresce (gancho Σ=Σ do razão já cabeado).
- **+** RPO/RTO por ente sem hardcode; o mesmo IaC serve entes de portes distintos.
- **−** Cria a área `infra/` e `docs/operacao/`, que precisam ser mantidas junto com
  o deploy real de cada ente (certificados, credenciais de cofre, endpoint de object
  storage) — nada disso versionado; entra pela esteira/cofre.
- **−** O drill exige um ambiente com PostgreSQL para rodar de verdade (scratch
  container em CI ou ambiente de operação); no repositório valida-se sintaxe e a
  lógica de verificação, não a execução ponta a ponta.
- **−** A trilha física (pgBackRest/PITR) e a lógica (pg_dump/age) têm operação
  distinta; o runbook (`docs/operacao/F0-runbook-tls-backup-restauracao.md`)
  documenta ambas para não haver ambiguidade em produção.
