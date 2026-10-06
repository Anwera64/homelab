#!/usr/bin/env bash
# ==============================================================================
# Nightly update. Brings in what was merged on GitHub (Renovate's image bumps,
# mostly) and restarts the stack when anything changed:
#
#   nothing new           -> nothing happens
#   new commits           -> fast-forward, server-stack-up.sh, prune old images
#   local edits or commits -> stop, change nothing
#
# Host setup (packages, units, the firewall) is bootstrap.sh's job: when a merge
# touches it, this says so and leaves it to a human. Runs from server-update.timer.
# ==============================================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$SERVER_DIR/../.." && pwd)"
# git refuses a root-run command in a checkout someone else owns.
OWNER="$(stat -c %U "$REPO_DIR")"

log() { echo "server update: $*"; }
repo() { runuser -u "$OWNER" -- git -C "$REPO_DIR" "$@"; }

if ! repo diff --quiet HEAD; then
  log "the checkout has local edits; nothing applied. See: git -C $REPO_DIR status"
  exit 1
fi

before="$(repo rev-parse HEAD)"
repo fetch --quiet
if ! repo merge --ff-only --quiet '@{upstream}'; then
  log "the checkout has commits origin does not; nothing applied. See: git -C $REPO_DIR status"
  exit 1
fi
after="$(repo rev-parse HEAD)"

if [ "$before" = "$after" ]; then
  log "up to date at ${after:0:7}"
  exit 0
fi

log "${before:0:7} -> ${after:0:7}"
"$SERVER_DIR/system/server-stack-up.sh"
docker image prune -f

if [ -n "$(repo diff --name-only "$before" "$after" -- hosts/server/bootstrap.sh hosts/server/system)" ]; then
  log "the host setup changed too: run sudo hosts/server/bootstrap.sh"
fi
