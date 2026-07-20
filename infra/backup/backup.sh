#!/usr/bin/env bash
# infra/backup/backup.sh — backup cifrado F0 (RAZ-36 / ADR-0020).
#
# Duas trilhas, escolhidas por METODO:
#   pgbackrest  -> físico/PITR, repo cifrado em object storage com Object Lock (WORM)
#   dump        -> lógico air-gapped: pg_dump custom-format cifrado com age
#
# Sem valor fixo de RPO/RTO/retenção no script: tudo vem de PARAMS_FILE e o script
# ABORTA se faltar (não há default de produção embutido).
#
# Segredos (senha do banco, chave de cifra, credenciais de object storage) vêm do
# COFRE/ambiente, nunca deste script nem do repositório.
set -euo pipefail

log() { printf '%s [backup] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
fail() { log "ERRO: $*"; exit 1; }

# --- Parâmetros do ente ------------------------------------------------------
: "${PARAMS_FILE:?defina PARAMS_FILE=infra/params/<ente>.env}"
[ -f "$PARAMS_FILE" ] || fail "PARAMS_FILE não encontrado: $PARAMS_FILE"
# shellcheck disable=SC1090
. "$PARAMS_FILE"

: "${ENTE_SLUG:?ENTE_SLUG ausente no PARAMS_FILE}"
: "${RETENCAO_FULL:?RETENCAO_FULL ausente no PARAMS_FILE}"
: "${RETENCAO_DIAS:?RETENCAO_DIAS ausente no PARAMS_FILE}"
: "${IMUTABILIDADE_DIAS:?IMUTABILIDADE_DIAS ausente no PARAMS_FILE}"
: "${PGHOST_ORIGEM:?}"; : "${PGPORT_ORIGEM:?}"; : "${PGDATABASE_ORIGEM:?}"

# Coerência: a janela de imutabilidade não pode ser menor que a retenção.
[ "$IMUTABILIDADE_DIAS" -ge "$RETENCAO_DIAS" ] \
  || fail "IMUTABILIDADE_DIAS ($IMUTABILIDADE_DIAS) < RETENCAO_DIAS ($RETENCAO_DIAS)"

METODO="${METODO:-dump}"          # dump | pgbackrest
TIPO="full"
while [ $# -gt 0 ]; do
  case "$1" in
    --tipo) TIPO="${2:?}"; shift 2 ;;
    --metodo) METODO="${2:?}"; shift 2 ;;
    *) fail "argumento desconhecido: $1" ;;
  esac
done
case "$TIPO" in full|incr) ;; *) fail "--tipo deve ser full|incr" ;; esac

case "$METODO" in
  pgbackrest)
    command -v pgbackrest >/dev/null || fail "pgbackrest não instalado"
    # Repo cifrado (aes-256) + Object Lock/WORM configurados em pgbackrest.conf
    # (ver pgbackrest.conf.exemplo). Retenção vem dos params, não do script.
    log "pgBackRest $TIPO stanza=$ENTE_SLUG (retenção-full=$RETENCAO_FULL)"
    pgbackrest --stanza="$ENTE_SLUG" \
      --type="$([ "$TIPO" = incr ] && echo incr || echo full)" \
      --repo1-retention-full="$RETENCAO_FULL" \
      backup
    log "OK — backup físico no repo cifrado/WORM. Imutabilidade: ${IMUTABILIDADE_DIAS}d (Object Lock)."
    ;;
  dump)
    # Trilha lógica air-gapped: pg_dump custom-format, cifrado com age.
    command -v pg_dump >/dev/null || fail "pg_dump não instalado"
    command -v age >/dev/null || fail "age não instalado (cifra do backup lógico)"
    : "${AGE_RECIPIENTS_FILE:?defina AGE_RECIPIENTS_FILE (chave pública de cifra, do cofre)}"
    [ "$TIPO" = full ] || fail "método dump só suporta --tipo full (lógico não é incremental)"
    OUT_DIR="${OUT_DIR:?defina OUT_DIR (destino do artefato; monte a mídia air-gapped)}"
    mkdir -p "$OUT_DIR"
    stamp="$(date -u +%Y%m%dT%H%M%SZ)"
    artefato="$OUT_DIR/${ENTE_SLUG}-${stamp}.dump.age"
    log "pg_dump -> cifra age -> $artefato"
    # Pipe direto: o dump em claro nunca toca o disco.
    PGPASSWORD="${PGPASSWORD:?senha do banco vem do cofre}" \
      pg_dump -h "$PGHOST_ORIGEM" -p "$PGPORT_ORIGEM" -d "$PGDATABASE_ORIGEM" \
        --format=custom --no-owner --no-privileges \
      | age --encrypt --recipients-file "$AGE_RECIPIENTS_FILE" --output "$artefato"
    log "OK — artefato cifrado gerado. Exporte para mídia air-gapped SOMENTE após o"
    log "     restore-drill aprovar (ver infra/restore/restore-drill.sh)."
    printf '%s\n' "$artefato"
    ;;
  *) fail "METODO desconhecido: $METODO (use pgbackrest|dump)" ;;
esac
