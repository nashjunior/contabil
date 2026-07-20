# infra — piso operacional F0 (fora do domínio Java)

Infraestrutura operacional do sistema contábil (SIAFIC). Implementa o piso de
segurança **F0** (RAZ-7) na parte que não vive nos módulos Java: TLS, backup
cifrado imutável e teste de restauração. Autoridade da decisão:
[ADR-0020](../docs/arquitetura-tecnica/adr/0020-f0-tls-backup-imutavel-restauracao.md).
Runbook e evidência de auditoria:
[docs/operacao](../docs/operacao/F0-runbook-tls-backup-restauracao.md).

```
infra/
  params/        RPO/RTO/retenção por ente (template exemplo.env.sample) — SEM hardcode
  tls/           TLS em todas as interfaces (borda, app, Postgres, gestão)
  backup/        backup cifrado + imutabilidade (pgBackRest/WORM | pg_dump+age air-gap)
  restore/       prova de restauração periódica + verificações de integridade
```

## Invariantes que esta camada respeita

- **Segredos no cofre.** Nenhum certificado, chave privada, senha ou chave de cifra
  é versionado. Os arquivos `*.exemplo`/`*.sample` são **modelos**; os valores
  reais vêm do cofre/da esteira. `.gitignore` já barra `*.env` (por isso o template
  do ente é `exemplo.env.sample`, e cada `infra/params/<ente>.env` fica fora do git).
- **Sem valor fixo indevido.** RPO/RTO/retenção não aparecem em script; são lidos de
  `infra/params/<ente>.env` e o script aborta se faltarem.
- **Append-only / integridade contábil.** A prova de restauração verifica a cadeia
  de hash de `auditoria_evento` e (quando existir) Σdébito=Σcrédito do razão.

## Uso rápido

```bash
# 1. parametrize o ente (copie o template; o arquivo real NÃO é versionado)
cp infra/params/exemplo.env.sample infra/params/ente-exemplo.env   # edite RPO/RTO/retenção

# 2. backup cifrado (lê o ente de PARAMS_FILE)
PARAMS_FILE=infra/params/ente-exemplo.env infra/backup/backup.sh --tipo full

# 3. prova de restauração + evidência de auditoria
PARAMS_FILE=infra/params/ente-exemplo.env \
  BACKUP_ARTEFATO=/caminho/backup.dump.age \
  infra/restore/restore-drill.sh
```
