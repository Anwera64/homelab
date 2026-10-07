#!/usr/bin/env bash
# ==============================================================================
# Disk guard. The media services may only run while the media disk is at /data:
# without it they would see an empty folder and report every file as missing.
#
#   disk present  -> start the media services that exist
#   disk gone     -> stop them, and release the dead mount
#   disk returned -> check its filesystem and mount it; "present" follows
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

# The device fstab names for /data, or nothing while it is unplugged. fstab, not
# .env, for the same reason as plain docker above.
fstab_device() {
  findmnt --fstab --evaluate -no SOURCE /data 2>/dev/null || true
}

# present: mounted and its device is still there. dead: a USB disk that dropped
# out leaves the mount behind, so the mount alone proves nothing. returned: the
# device is back but nothing mounted it, because fstab only mounts at boot.
disk_state() {
  local source
  if ! mountpoint -q /data; then
    if [ -b "$(fstab_device)" ]; then
      echo returned
    else
      echo absent
    fi
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
    returned)
      log "media disk is back: checking its filesystem"
      # A pulled disk has an unfinished journal, which e2fsck replays. 4 and up
      # means errors it would not fix alone: those wait for a person.
      local device status=0
      device="$(fstab_device)"
      e2fsck -p "$device" || status=$?
      if [ "$status" -ge 4 ] || ! mount /data; then
        log "the media disk needs a manual check (e2fsck exit $status): left unmounted"
      fi
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
