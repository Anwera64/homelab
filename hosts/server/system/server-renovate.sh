#!/usr/bin/env bash
# ==============================================================================
# One Renovate run: it looks for newer versions of what this repo pins, opens
# the weekly bump PRs and merges the ones renovate.json allows once their CI is
# green. Runs from server-renovate.timer, every hour.
#
# Renovate needs a GitHub token (RENOVATE_TOKEN in .env). Until it is there the
# run is skipped. compose reads the token from .env; this script never prints it.
# ==============================================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

log() { echo "renovate: $*"; }

if ! grep -qsE '^RENOVATE_TOKEN=.+' "$SERVER_DIR/.env"; then
  log "RENOVATE_TOKEN is not set in $SERVER_DIR/.env; skipped"
  exit 0
fi

# Attached, so the run's output is this unit's log too, and its exit code is the unit's.
docker compose --project-directory "$SERVER_DIR" --profile renovate up --exit-code-from renovate renovate
