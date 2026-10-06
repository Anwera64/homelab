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

# The compose file's "media" profile, by container name (tests keep the two lists
# equal). Plain docker, not compose: the guard must work whatever state .env is in.
MEDIA_SERVICES=(qbittorrent sonarr radarr bazarr jellyfin cleanuparr maintainerr recyclarr)
INTERVAL=15

log() { echo "disk guard: $*"; }

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
      docker start "${MEDIA_SERVICES[@]}" >/dev/null 2>&1 || log "not all of them exist yet (bootstrap.sh creates them)"
      ;;
    dead)
      log "media disk dropped out: stopping the media services"
      docker stop "${MEDIA_SERVICES[@]}" >/dev/null 2>&1 || true
      umount -l /data || true
      ;;
    absent)
      log "no media disk: the media services stay stopped"
      docker stop "${MEDIA_SERVICES[@]}" >/dev/null 2>&1 || true
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
