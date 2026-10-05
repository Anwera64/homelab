#!/usr/bin/env bash
# ==============================================================================
# Battery watcher for the laptop. The battery is the server's small UPS:
#
#   - conservation mode stays on, which holds the charge near 60% and slows wear
#   - on battery at or below BATTERY_SHUTDOWN_PERCENT (10), the stack is stopped
#     and the machine powers off, before the battery does it uncleanly
#
# Runs as a service; --once does a single pass. DRY_RUN=1 only reports.
# ==============================================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONSERVATION=/sys/bus/platform/drivers/ideapad_acpi/VPC2004:00/conservation_mode
THRESHOLD="${BATTERY_SHUTDOWN_PERCENT:-10}"
INTERVAL=60

log() { echo "battery: $*"; }
compose() { docker compose --project-directory "$SERVER_DIR" --profile media "$@"; }

check() {
  local online capacity

  if [ -w "$CONSERVATION" ] && [ "$(cat "$CONSERVATION")" != "1" ]; then
    echo 1 > "$CONSERVATION"
    log "conservation mode switched on"
  fi

  online="$(cat /sys/class/power_supply/ADP0/online 2>/dev/null || echo 1)"
  capacity="$(cat /sys/class/power_supply/BAT0/capacity 2>/dev/null || echo 100)"
  if [ "$online" = "0" ] && [ "$capacity" -le "$THRESHOLD" ]; then
    log "on battery at ${capacity}%: stopping the stack and powering off"
    if [ -n "${DRY_RUN:-}" ]; then
      log "dry run: nothing was stopped"
      return
    fi
    compose stop || true
    systemctl poweroff
  fi
}

case "${1:-}" in
  --once)
    check
    exit 0
    ;;
esac

while true; do
  check
  sleep "$INTERVAL"
done
