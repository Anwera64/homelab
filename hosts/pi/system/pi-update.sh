#!/usr/bin/env bash
# ==============================================================================
# Nightly update, as on the server (hosts/server/system/server-update.sh). Brings
# in what was merged on GitHub (Renovate's image bumps, the Caddyfile) and
# restarts the stack when anything changed:
#
#   nothing new           -> nothing happens
#   new commits           -> fast-forward, start the stack, prune old images
#   a start that failed   -> back to the last commit that started, an alert, and
#                            the same change tried again the next night
#   local edits or commits -> stop, change nothing, an alert
#
# The last commit the stack started from is kept as the ref APPLIED_REF, so a
# night that stopped after the fast-forward is not mistaken for up to date.
#
# Host setup (packages, units) is bootstrap.sh's job: when a merge touches it,
# this says so and leaves it to a human. Runs from pi-update.timer.
# ==============================================================================
set -euo pipefail

PI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$PI_DIR/../.." && pwd)"
# git refuses a root-run command in a checkout someone else owns.
OWNER="$(stat -c %U "$REPO_DIR")"
APPLIED_REF=refs/pi-update/applied
# Only the tests point it elsewhere. ntfy publishes on the Pi's loopback.
NTFY_URL="${PI_NTFY_URL:-http://127.0.0.1:2586/homelab}"
# Why the run failed, for the alert. Set before each exit that is not a success.
FAILURE="the run stopped early"

log() { echo "pi update: $*"; }
repo() { runuser -u "$OWNER" -- git -C "$REPO_DIR" "$@"; }
compose() { docker compose --project-directory "$PI_DIR" "$@"; }

# $1: the title, $2: the message. Through the Pi's own ntfy, as the pi-update user
# (README.md). Without a token nothing is sent; a send that fails is only logged.
notify() {
  local token
  token="$(sed -n 's/^NTFY_UPDATE_TOKEN=//p' "$PI_DIR/.env" 2>/dev/null | tail -1)"
  [ -n "$token" ] || return 0
  curl -fsS --max-time 10 -o /dev/null \
    -H "Authorization: Bearer $token" -H "Title: lemonpi update: $1" -H "Priority: high" -H "Tags: warning" \
    -d "$2 See: journalctl -u pi-update" "$NTFY_URL" \
    || log "could not send the alert to ntfy"
}
# A failed run says why, through ntfy, and still ends with its own status.
on_exit() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    log "$FAILURE"
    notify "failed" "$FAILURE." || true
  fi
  exit "$status"
}
trap on_exit EXIT

# Services that mount one file from the checkout. A merge writes a new file, and the
# running container keeps reading the old one, so they are recreated when it changed.
MOUNTED_FILES=(caddy:hosts/pi/caddy/Caddyfile unbound:hosts/pi/unbound/unbound.conf)

# $1, $2: the commit the stack ran and the one checked out now. Starts the stack and
# waits for its health checks. Called from an if, where set -e does not apply.
start() {
  local recreate=() entry
  compose up -d --remove-orphans --wait || return 1
  for entry in "${MOUNTED_FILES[@]}"; do
    [ -z "$(repo diff --name-only "$1" "$2" -- "${entry#*:}")" ] || recreate+=("${entry%%:*}")
  done
  [ "${#recreate[@]}" -eq 0 ] || compose up -d --force-recreate --wait "${recreate[@]}" || return 1
}

if ! repo diff --quiet HEAD; then
  FAILURE="the checkout has local edits; nothing applied. See: git -C $REPO_DIR status"
  exit 1
fi

# Before the ref existed, the checkout's HEAD was what the stack ran.
repo rev-parse --verify -q "$APPLIED_REF" >/dev/null || repo update-ref "$APPLIED_REF" HEAD
before="$(repo rev-parse "$APPLIED_REF")"
FAILURE="git fetch failed; nothing applied"
repo fetch --quiet
if ! repo merge --ff-only --quiet '@{upstream}'; then
  FAILURE="the checkout has commits origin does not have; nothing applied. See: git -C $REPO_DIR status"
  exit 1
fi
after="$(repo rev-parse HEAD)"

if [ "$before" = "$after" ]; then
  log "up to date at ${after:0:7}"
  exit 0
fi

log "${before:0:7} -> ${after:0:7}"
if ! start "$before" "$after"; then
  # The Pi is the house DNS: rather than wait for a fix, go back to what last started.
  # The next night fast-forwards to the same commit and tries it again.
  log "the stack did not start at ${after:0:7}; rolling back to ${before:0:7}"
  repo reset --hard --quiet "$before"
  if start "$after" "$before"; then
    FAILURE="the stack did not start at ${after:0:7}, so it was rolled back to ${before:0:7}, which runs now. Tomorrow night tries again"
  else
    FAILURE="the stack did not start at ${after:0:7}, and the rollback failed too: ${before:0:7} did not start either"
  fi
  exit 1
fi
FAILURE="${after:0:7} started, but the run failed after that"
docker image prune -f
repo update-ref "$APPLIED_REF" "$after"

if [ -n "$(repo diff --name-only "$before" "$after" -- hosts/pi/bootstrap.sh hosts/pi/system)" ]; then
  log "the host setup changed too: run sudo hosts/pi/bootstrap.sh"
  notify "run bootstrap" "Applied ${before:0:7} -> ${after:0:7}, which changes the host setup: run sudo hosts/pi/bootstrap.sh."
fi
