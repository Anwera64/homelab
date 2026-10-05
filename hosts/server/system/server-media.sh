#!/usr/bin/env bash
# ==============================================================================
# Disk guard. The media services may only run while the media disk is at /data:
# without it they would see an empty folder and report every file as missing.
#
#   disk present  -> start the media services that exist
#   disk gone     -> stop them, and release the dead mount
#
# It never creates a container: bootstrap.sh decides what runs, the guard only
# pauses and resumes it. Runs as a service; --once does a single pass.
# ==============================================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# The compose file's "media" profile (tests keep the two lists equal).
MEDIA_SERVICES=(qbittorrent sonarr radarr bazarr jellyfin cleanuparr maintainerr recyclarr)
INTERVAL=15

log() { echo "disk guard: $*"; }
compose() { docker compose --project-directory "$SERVER_DIR" --profile media "$@"; }

# present: mounted and its device is still there. dead: a USB disk that dropped
# out leaves the mount behind, so the mount alone proves nothing.
disk_state() {
  local source
  if ! mountpoint -q /data; then
    echo absent
    return
  fi
  source="$(findmnt -no SOURCE /data)"
  if [ -b "$source" ]; then
    echo present
  else
    echo dead
  fi
}

apply() {
  case "$1" in
    present)
      log "media disk present: starting the media services"
      compose start "${MEDIA_SERVICES[@]}" || log "nothing to start yet (bootstrap.sh creates them)"
      ;;
    dead)
      log "media disk dropped out: stopping the media services"
      compose stop "${MEDIA_SERVICES[@]}" || true
      umount -l /data || true
      ;;
    absent)
      log "no media disk: the media services stay stopped"
      compose stop "${MEDIA_SERVICES[@]}" || true
      ;;
  esac
}

case "${1:-}" in
  --once)
    apply "$(disk_state)"
    exit 0
    ;;
esac

last=""
while true; do
  state="$(disk_state)"
  if [ "$state" != "$last" ]; then
    apply "$state"
    last="$state"
  fi
  sleep "$INTERVAL"
done
