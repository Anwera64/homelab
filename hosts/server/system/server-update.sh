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
#
# Every run leaves three numbers for the node exporter (the Updates section of
# the server dashboard): when it ran, whether it ended well, and when a run last
# applied something.
# ==============================================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$SERVER_DIR/../.." && pwd)"
# git refuses a root-run command in a checkout someone else owns.
OWNER="$(stat -c %U "$REPO_DIR")"
TEXTFILE_DIR="${SERVER_TEXTFILE_DIR:-/var/lib/node_exporter/textfile}"
PROM="$TEXTFILE_DIR/server_update.prom"
APPLIED=0

log() { echo "server update: $*"; }
repo() { runuser -u "$OWNER" -- git -C "$REPO_DIR" "$@"; }

# $1: the run's exit status. The time of the last applied update is carried over
# from the previous file on a night that applied nothing.
record() {
  local success=0 applied tmp
  [ "$1" -eq 0 ] && success=1
  applied="$(sed -n 's/^server_update_last_applied_timestamp_seconds //p' "$PROM" 2>/dev/null || true)"
  [ "$APPLIED" -eq 1 ] && applied="$(date +%s)"
  mkdir -p "$TEXTFILE_DIR"
  # Written beside the file and moved over it, so the exporter never reads half of one.
  # 644: the exporter does not run as root.
  tmp="$(mktemp "$PROM.XXXXXX")"
  {
    echo "server_update_last_run_timestamp_seconds $(date +%s)"
    echo "server_update_last_run_success $success"
    [ -z "$applied" ] || echo "server_update_last_applied_timestamp_seconds $applied"
  } > "$tmp"
  chmod 644 "$tmp"
  mv "$tmp" "$PROM"
}
# A run that could not write its numbers still ends with its own status.
on_exit() {
  local status=$?
  record "$status" || true
  exit "$status"
}
trap on_exit EXIT

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
APPLIED=1

if [ -n "$(repo diff --name-only "$before" "$after" -- hosts/server/bootstrap.sh hosts/server/system)" ]; then
  log "the host setup changed too: run sudo hosts/server/bootstrap.sh"
fi
