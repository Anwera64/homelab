#!/usr/bin/env bash
# ==============================================================================
# Keeps the Wi-Fi on 5 GHz when it can be. The router gives one network name on
# 2.4 and 5 GHz; wpa_supplicant never leaves a strong signal by itself, so once
# the card is on 2.4 GHz it stays there, several times slower.
#
#   on 5 GHz, or not connected          -> nothing happens
#   on 2.4 GHz, same network on 5 GHz   -> asks wpa_supplicant to roam there
#
# It only asks for a roam. No band is excluded, so 2.4 GHz is still there when
# 5 GHz is not, and the Wi-Fi settings are never touched. Runs from
# server-wifi-band.timer, every minute; each move is one line in the journal.
# ==============================================================================
set -euo pipefail

SYS="${SERVER_WIFI_SYS:-/sys/class/net}"
# A scan of both bands takes over ten seconds on this card.
SCAN_WAIT="${SERVER_WIFI_SCAN_WAIT:-15}"
# Below this, 5 GHz is not worth leaving a good 2.4 GHz signal for.
MIN_SIGNAL=-70

log() { echo "wifi band: $*"; }

command -v wpa_cli >/dev/null 2>&1 || exit 0

IF="${SERVER_WIFI_IF:-}"
if [ -z "$IF" ]; then
  for dir in "$SYS"/*/wireless; do
    if [ -d "$dir" ]; then
      IF="$(basename "$(dirname "$dir")")"
      break
    fi
  done
fi
[ -n "$IF" ] || exit 0

status="$(wpa_cli -i "$IF" status 2>/dev/null || true)"
field() { sed -n "s/^$1=//p" <<< "$status" | head -1; }

[ "$(field wpa_state)" = COMPLETED ] || exit 0
freq="$(field freq)"
ssid="$(field ssid)"
if [ -z "$freq" ] || [ "$freq" -ge 4000 ]; then
  exit 0
fi

wpa_cli -i "$IF" scan >/dev/null
sleep "$SCAN_WAIT"

# bssid, frequency, signal, flags, name: tab-separated. The strongest 5 GHz radio
# with exactly this network's name.
best="$(wpa_cli -i "$IF" scan_results \
  | awk -F'\t' -v ssid="$ssid" -v min="$MIN_SIGNAL" '$2 >= 5000 && $2 < 5925 && $5 == ssid && $3 >= min { print $3, $2, $1 }' \
  | sort -rn | head -1)"
[ -n "$best" ] || exit 0

read -r signal target bssid <<< "$best"
log "$freq MHz -> $target MHz ($bssid, $signal dBm)"
answer="$(wpa_cli -i "$IF" roam "$bssid")"
[ "$answer" = OK ] || log "wpa_supplicant refused the roam: $answer"
