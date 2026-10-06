#!/usr/bin/env bash
# ==============================================================================
# Builds and starts the stack as it should run now: the media services only
# while the media disk is mounted, then the disk guard has its say. Used by
# bootstrap.sh and by the nightly update (server-update.sh), so both start the
# stack the same way.
# ==============================================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker compose --project-directory "$SERVER_DIR" build
if mountpoint -q /data; then
  docker compose --project-directory "$SERVER_DIR" up -d --remove-orphans
else
  # Without the disk the media services are left out; they cannot mount /data.
  COMPOSE_PROFILES="" docker compose --project-directory "$SERVER_DIR" up -d --remove-orphans
fi
"$SERVER_DIR/system/server-media.sh" --once
