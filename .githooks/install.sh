#!/bin/sh
# Ativa os git hooks do SIAFIC neste clone/worktree (ADR-0037 / RAZ-133).
# Idempotente: rode uma vez por clone. Worktrees compartilham o mesmo .git/config,
# então basta rodar uma vez no repo.
set -e
root=$(git rev-parse --show-toplevel)
git config core.hooksPath .githooks
chmod +x "$root/.githooks/commit-msg" 2>/dev/null || true
echo "OK: core.hooksPath = .githooks — guarda commit-msg (RAZ-133/ADR-0037) ativa."
