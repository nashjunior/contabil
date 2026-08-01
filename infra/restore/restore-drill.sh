#!/usr/bin/env bash
# infra/restore/restore-drill.sh — PROVA de restauração F0 (RAZ-36 / ADR-0020).
#
# Restaura o último backup numa base scratch DESCARTÁVEL, verifica integridade
# (migrações + cadeia de hash append-only + órfãos + Σd=Σc quando o razão existir),
# MEDE o RTO e emite um relatório de evidência datado para auditoria TCE/ANPD.
#
# Códigos de saída: 0 = APROVADO | 1 = REPROVADO | 2 = erro operacional.
# RPO/RTO alvo vêm de PARAMS_FILE — sem valor fixo no script (ADR-0020, item 4).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
log()  { printf '%s [drill] %s\n' "$(date -u +%FT%TZ)" "$*" >&2; }
fail() { log "ERRO OPERACIONAL: $*"; exit 2; }

# --- Parâmetros do ente (sem default de produção embutido) -------------------
: "${PARAMS_FILE:?defina PARAMS_FILE=infra/params/<ente>.env}"
[ -f "$PARAMS_FILE" ] || fail "PARAMS_FILE não encontrado: $PARAMS_FILE"
# shellcheck disable=SC1090
. "$PARAMS_FILE"

: "${ENTE_SLUG:?}"
: "${RPO_ALVO_MIN:?RPO_ALVO_MIN ausente no PARAMS_FILE}"
: "${RTO_ALVO_MIN:?RTO_ALVO_MIN ausente no PARAMS_FILE}"
: "${PGHOST_SCRATCH:?}"; : "${PGPORT_SCRATCH:?}"; : "${PGDATABASE_SCRATCH:?}"
: "${EVIDENCIA_DIR:?}"
PGUSER_SCRATCH="${PGUSER_SCRATCH:-postgres}"   # deve bypassar RLS na base scratch
METODO="${METODO:-dump}"

command -v psql >/dev/null || fail "psql não instalado"

export PGHOST="$PGHOST_SCRATCH" PGPORT="$PGPORT_SCRATCH" PGUSER="$PGUSER_SCRATCH"
[ -n "${PGPASSWORD_SCRATCH:-}" ] && export PGPASSWORD="$PGPASSWORD_SCRATCH"

adm() { psql -v ON_ERROR_STOP=1 -d postgres   -Atqc "$1"; }
db()  { psql -v ON_ERROR_STOP=1 -d "$PGDATABASE_SCRATCH" "$@"; }

cleanup() {
    adm "drop database if exists \"$PGDATABASE_SCRATCH\" with (force);" >/dev/null 2>&1 || true
}
trap cleanup EXIT

veredito="APROVADO"; motivo=""
reprova() { veredito="REPROVADO"; motivo="${motivo:+$motivo; }$1"; log "REPROVA: $1"; }

# --- 1. base scratch limpa ---------------------------------------------------
log "provisiona base scratch descartável: $PGDATABASE_SCRATCH"
adm "drop database if exists \"$PGDATABASE_SCRATCH\" with (force);"
adm "create database \"$PGDATABASE_SCRATCH\";"

# --- 2. restauração (mede RTO) ----------------------------------------------
t0=$(date +%s)
case "$METODO" in
  dump)
    : "${BACKUP_ARTEFATO:?defina BACKUP_ARTEFATO=/caminho/<ente>-<stamp>.dump.age}"
    [ -f "$BACKUP_ARTEFATO" ] || fail "artefato não encontrado: $BACKUP_ARTEFATO"
    command -v age >/dev/null        || fail "age não instalado"
    command -v pg_restore >/dev/null || fail "pg_restore não instalado"
    : "${AGE_IDENTITY_FILE:?defina AGE_IDENTITY_FILE (chave privada de decifra, do cofre)}"
    log "decifra (age) + pg_restore -> $PGDATABASE_SCRATCH"
    age --decrypt --identity "$AGE_IDENTITY_FILE" "$BACKUP_ARTEFATO" \
      | pg_restore --no-owner --no-privileges -d "$PGDATABASE_SCRATCH"
    # idade do artefato (mtime) -> RPO observado
    backup_epoch=$(stat -c %Y "$BACKUP_ARTEFATO" 2>/dev/null || date -r "$BACKUP_ARTEFATO" +%s)
    ;;
  pgbackrest)
    command -v pgbackrest >/dev/null || fail "pgbackrest não instalado"
    fail "restore físico pgBackRest é conduzido pelo runbook (restaura cluster scratch e valida). \
Este drill implementa a trilha lógica (dump). Ver docs/operacao/F0-runbook-tls-backup-restauracao.md"
    ;;
  *) fail "METODO desconhecido: $METODO (use dump|pgbackrest)" ;;
esac
t1=$(date +%s)
rto_med_min=$(( (t1 - t0 + 59) / 60 ))
now=$(date +%s)
rpo_obs_min=$(( (now - backup_epoch + 59) / 60 ))

# --- 3. tabelas críticas presentes? -----------------------------------------
for t in flyway_schema_history ente auditoria_evento; do
    if [ "$(db -Atqc "select to_regclass('$t') is not null;")" != "t" ]; then
        reprova "tabela crítica ausente após restauração: $t"
    fi
done

# --- 4. verificações de integridade -----------------------------------------
if verif_out=$(db -f "$SCRIPT_DIR/verificacoes.sql" 2>&1); then
    if printf '%s' "$verif_out" | grep -q 'FALHA'; then
        reprova "verificação de integridade reprovou"
    fi
else
    reprova "erro ao executar verificacoes.sql"
    verif_out="${verif_out:-<sem saída>}"
fi

# --- 5. gancho do razão (Σd=Σc) — só se a tabela já existir ------------------
if [ "$(db -Atqc "select to_regclass('lancamento') is not null;")" = "t" ]; then
    if razao_out=$(db -f "$SCRIPT_DIR/verificacoes-razao.sql" 2>&1); then
        if printf '%s' "$razao_out" | grep -q 'FALHA'; then
            reprova "razão desbalanceado (Σdébito<>Σcrédito)"
        fi
    else
        reprova "erro ao executar verificacoes-razao.sql"
    fi
else
    razao_out="N/A — tabela do razão ainda não existe nesta base (gancho inerte)"
fi

# --- 5b. gancho do outbox de entrega (órfãos + RLS) — só se a tabela já existir --
if [ "$(db -Atqc "select to_regclass('outbox_mensagem') is not null;")" = "t" ]; then
    if outbox_out=$(db -f "$SCRIPT_DIR/verificacoes-outbox.sql" 2>&1); then
        if printf '%s' "$outbox_out" | grep -q 'FALHA'; then
            reprova "outbox de entrega com órfão(s) ou RLS não forçada"
        fi
    else
        reprova "erro ao executar verificacoes-outbox.sql"
    fi
else
    outbox_out="N/A — tabela do outbox ainda não existe nesta base (gancho inerte)"
fi

# --- 6. porta de RTO/RPO contra o alvo do ente ------------------------------
[ "$rto_med_min" -le "$RTO_ALVO_MIN" ] || reprova "RTO medido ${rto_med_min}min > alvo ${RTO_ALVO_MIN}min"
[ "$rpo_obs_min" -le "$RPO_ALVO_MIN" ] || reprova "idade do backup ${rpo_obs_min}min > RPO alvo ${RPO_ALVO_MIN}min"

# --- 7. evidência de auditoria ----------------------------------------------
mkdir -p "$EVIDENCIA_DIR"
stamp=$(date -u +%Y%m%dT%H%M%SZ)
report="$EVIDENCIA_DIR/f0-restore-${ENTE_SLUG}-${stamp}.md"
{
    echo "# Evidência de restauração F0 — ${ENTE_SLUG}"
    echo
    echo "- Data (UTC): $(date -u +%FT%TZ)"
    echo "- Ente: \`${ENTE_SLUG}\`"
    echo "- Método: ${METODO}"
    echo "- Artefato: ${BACKUP_ARTEFATO:-<pgbackrest>}"
    echo "- RPO alvo: ${RPO_ALVO_MIN} min | RPO observado (idade do backup): ${rpo_obs_min} min"
    echo "- RTO alvo: ${RTO_ALVO_MIN} min | RTO medido (restauração + verificação): ${rto_med_min} min"
    echo "- **Veredito: ${veredito}**"
    [ -n "$motivo" ] && echo "- Motivo: ${motivo}"
    echo
    echo "## Verificações de integridade (schema public — ente/auditoria)"
    echo '```'
    printf '%s\n' "$verif_out"
    echo '```'
    echo
    echo "## Razão — partidas dobradas (Σdébito = Σcrédito)"
    echo '```'
    printf '%s\n' "$razao_out"
    echo '```'
    echo
    echo "## Outbox de entrega — órfãos + RLS forçada (RAZ-9/RAZ-70)"
    echo '```'
    printf '%s\n' "$outbox_out"
    echo '```'
} > "$report"

log "evidência gravada: $report"
printf '%s [drill] veredito=%s (RTO=%smin RPO=%smin)\n' \
    "$(date -u +%FT%TZ)" "$veredito" "$rto_med_min" "$rpo_obs_min" >&2

[ "$veredito" = "APROVADO" ] && exit 0 || exit 1
