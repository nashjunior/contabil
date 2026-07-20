# F0 — Runbook: TLS, backup imutável e teste de restauração

Piso de segurança **F0** (RAZ-7), trilha IaC (RAZ-36), na parte fora do domínio
Java. Autoridade: [ADR-0020](../arquitetura-tecnica/adr/0020-f0-tls-backup-imutavel-restauracao.md).
Artefatos executáveis: [`infra/`](../../infra/). Base legal: Decreto 10.540/2020
(SIAFIC — integridade, disponibilidade, rastreabilidade), LGPD (Lei 13.709/2018).

Este runbook é a **evidência operacional mínima para auditoria TCE/ANPD**: mostra
que TLS está aplicado, que o backup é cifrado e imutável, e que a restauração é
**testada e medida** — não apenas gerada.

---

## 1. TLS em todas as interfaces

Piso **TLS 1.2, preferência 1.3**, apenas suítes AEAD. Nenhum tráfego em claro.
Config: [`infra/tls/`](../../infra/tls/). Matriz de interfaces e enforce:

| Interface | Config | Como conferir |
| --- | --- | --- |
| Cliente → sistema (público) | `nginx-razao.conf.exemplo` | `nmap --script ssl-enum-ciphers -p 443 <host>`; redirect 80→443; HSTS presente |
| App (Spring) / gestão | `application-tls.yml.exemplo` | actuator em porta TLS separada com bind interno |
| App → PostgreSQL | `DB_URL=...?sslmode=verify-full&sslrootcert=...` | `SHOW ssl` na sessão; `pg_stat_ssl` mostra `ssl=t` |
| PostgreSQL (servidor) | `postgresql-tls.conf.exemplo` | `ssl=on`; `pg_hba.conf` só com `hostssl` para a base |
| App → publicação (Transparência/PNCP/SICONFI) | cliente HTTP | HTTPS obrigatório, sem downgrade |

**Checklist de aceite:** todas as linhas acima verificadas e anexadas ao dossiê.
Certificados/chaves nunca no repositório — vêm do cofre/ACME.

## 2. Backup cifrado e imutável

Duas trilhas (ADR-0020, item 2); o ente escolhe pela existência de object storage.
Cifra AES-256 com chave **do cofre**. Config: [`infra/backup/`](../../infra/backup/).

### Trilha A — física/PITR (com object storage)
- pgBackRest, repo cifrado (`repo1-cipher-type=aes-256-cbc`) em bucket S3 com
  **Object Lock (WORM, modo compliance)**, retenção ≥ `IMUTABILIDADE_DIAS`.
- WAL archiving liga PITR e derruba o RPO.
- Full/incremental:
  ```bash
  PARAMS_FILE=infra/params/<ente>.env METODO=pgbackrest infra/backup/backup.sh --tipo full
  PARAMS_FILE=infra/params/<ente>.env METODO=pgbackrest infra/backup/backup.sh --tipo incr
  ```

### Trilha B — lógica air-gapped (sem object storage)
- `pg_dump` custom-format cifrado com `age`; o dump em claro **nunca toca o disco**
  (pipe direto). Exportar para mídia offline **só depois** do drill aprovar.
  ```bash
  PARAMS_FILE=infra/params/<ente>.env METODO=dump \
    OUT_DIR=/mnt/airgap AGE_RECIPIENTS_FILE=/run/secrets/age.pub \
    PGPASSWORD=$DB_PASSWORD infra/backup/backup.sh --tipo full
  ```

**Imutabilidade é o ponto:** cifra protege confidencialidade; Object Lock/WORM ou
air-gap protege o backup contra apagamento/adulteração por credencial comprometida.

## 3. Teste de restauração (prova, não só geração)

`infra/restore/restore-drill.sh` restaura o último backup numa **base scratch
descartável**, roda verificações de integridade, **mede o RTO** e grava um
relatório de evidência datado. Sai **REPROVADO (exit 1)** se qualquer verificação
falhar ou se RTO/RPO medidos violarem o alvo do ente.

```bash
PARAMS_FILE=infra/params/<ente>.env \
  BACKUP_ARTEFATO=/mnt/airgap/<ente>-<stamp>.dump.age \
  AGE_IDENTITY_FILE=/run/secrets/age.key \
  infra/restore/restore-drill.sh
```

Verificações ([`verificacoes.sql`](../../infra/restore/verificacoes.sql)), ancoradas
no schema real do repo (`public`; coluna de isolamento `ente_id`, ADR-0015):

1. **Migrações** — `flyway_schema_history` sem falhas.
2. **Auditoria append-only** — sequência **contígua por ente** e `hash_anterior`
   **encadeado**. Prova que o backup preservou a cadeia de hash imutável.
3. **Integridade referencial** — sem eventos de auditoria órfãos (sem `ente`).
4. **Razão (Σdébito=Σcrédito)** — gancho [`verificacoes-razao.sql`](../../infra/restore/verificacoes-razao.sql),
   acionado automaticamente quando a tabela `lancamento` existe na base restaurada;
   com a migração do razão (V1) já no repo, o gancho é **ativo** (Σd=Σc por fato + global).

> **A instância scratch deve rodar as verificações como superusuário ou papel
> BYPASSRLS** — `auditoria_evento` tem `force row level security`; sem bypass a
> leitura de verificação viria vazia e mascararia uma restauração parcial.

### Validação do oráculo (executada nesta entrega)

O oráculo de integridade foi exercido ponta a ponta contra um PostgreSQL 16 com as
migrações reais V1–V3 aplicadas:

- **Base íntegra** (cadeia de hash contígua e encadeada) → 6 verificações `OK`,
  drill sairia `APROVADO`.
- **Base corrompida** (elo de hash quebrado + lacuna de sequência) → `FALHA` em
  `auditoria_sequencia_contigua` e `auditoria_hash_encadeado`, drill sairia
  `REPROVADO (exit 1)`.

Ou seja: a restauração é comprovada por integridade contábil, não só por "o
`pg_restore` retornou 0".

## 4. RPO/RTO por ente/contrato

Sem número fixo no código. Parâmetros em [`infra/params/<ente>.env`](../../infra/params/exemplo.env.sample)
(`RPO_ALVO_MIN`, `RTO_ALVO_MIN`, `RETENCAO_*`, `IMUTABILIDADE_DIAS`, crons); os
scripts **abortam** se a variável faltar. O drill compara RTO medido e idade do
backup contra os alvos e reprova se violados.

## 5. Agenda e evidência de auditoria

- **Backup:** `BACKUP_FULL_CRON` / `BACKUP_INCR_CRON` do ente.
- **Prova de restauração:** `DRILL_CRON` do ente (mínimo recomendado F0: semanal).
- **Evidência:** cada execução do drill grava `EVIDENCIA_DIR/f0-restore-<ente>-<stamp>.md`
  com id do backup, RPO observado, RTO medido, resultado de cada verificação e
  veredito. **Esse arquivo é o que se apresenta ao TCE/ANPD.** Um drill `REPROVADO`
  deve abrir incidente e bloquear a promoção da cópia air-gapped à mídia offline.

## 6. Dossiê de aceite F0 (RAZ-36)

- [ ] Saída de cifras TLS de cada interface da matriz (seção 1).
- [ ] Config do repo de backup cifrado + evidência de Object Lock/WORM **ou**
      procedimento air-gapped homologado (seção 2).
- [ ] Último relatório de evidência do drill com veredito `APROVADO` (seção 3).
- [ ] `infra/params/<ente>.env` com RPO/RTO do contrato do ente (seção 4).
