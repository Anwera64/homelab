#!/usr/bin/env bash
# ==============================================================================
# Builds and starts the stack as it should run now: the media services only
# while the media disk is mounted, then the disk guard has its say. Used by
# bootstrap.sh and by the nightly update (server-update.sh), so both start the
# stack the same way.
#
# Before the start, Jellyfin's settings folder is handed to the user Jellyfin
# runs as, if anything in it belongs to someone else.
# ==============================================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# The user the media apps run as. Only the tests set another one.
APP_UID="${SERVER_APP_UID:-1000}"

docker compose --project-directory "$SERVER_DIR" build

# Jellyfin ran as root before it had a user of its own, and what it wrote then is
# root's. Stopped first, so a root Jellyfin cannot add more while this runs.
CONFIG_PATH=""
[ ! -f "$SERVER_DIR/.env" ] || CONFIG_PATH="$(sed -n 's/^CONFIG_PATH=//p' "$SERVER_DIR/.env" | tail -1)"
JELLYFIN_DIR="$CONFIG_PATH/jellyfin"
if [ -n "$CONFIG_PATH" ] && [ -d "$JELLYFIN_DIR" ] && [ -n "$(find "$JELLYFIN_DIR" ! -uid "$APP_UID" -print -quit)" ]; then
  echo "Jellyfin's settings are not all owned by user $APP_UID: stopping Jellyfin and re-owning $JELLYFIN_DIR"
  docker stop jellyfin >/dev/null 2>&1 || true
  chown -R "$APP_UID:$APP_UID" "$JELLYFIN_DIR"
fi

if mountpoint -q /data; then
  docker compose --project-directory "$SERVER_DIR" up -d --remove-orphans
else
  # Without the disk the media services are left out; they cannot mount /data.
  COMPOSE_PROFILES="" docker compose --project-directory "$SERVER_DIR" up -d --remove-orphans
fi
"$SERVER_DIR/system/server-media.sh" --once
