#!/usr/bin/env bash
# ==============================================================================
# Moves Gluetun off a VPN server that does not hold a forwarded port. Some of
# the provider's servers refuse the port or lose it every few minutes, and
# Gluetun does not ask again after a refusal at its start.
#
#   port forwarding off in .env            -> nothing happens
#   a port, lost six times an hour or less -> nothing happens
#   no port for ten minutes                -> recreates the VPN's containers
#   a port lost more often than that       -> the same
#   a port qBittorrent does not listen on  -> gives it to qBittorrent
#
# A recreate makes Gluetun pick a server again, among those named in .env
# (VPN_SERVER_HOSTNAMES). After three recreations that brought no port, any
# server in the country may be used; the named ones come back at 04:00. After
# six, it tries once an hour: each recreate restarts qBittorrent.
#
# Runs from server-vpn-watchdog.timer, every minute; each recreate is one line
# in the journal and a count for the Downloads dashboard.
# ==============================================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${SERVER_ENV_FILE:-$SERVER_DIR/.env}"
STATE_DIR="${SERVER_VPN_STATE_DIR:-/var/lib/server-vpn-watchdog}"
TEXTFILE_DIR="${SERVER_TEXTFILE_DIR:-/var/lib/node_exporter/textfile}"
NOW="${SERVER_NOW:-$(date +%s)}"
STATE="$STATE_DIR/state"

# Seconds without a port before a recreate, and between recreates once they keep failing.
WAIT=600
LONG_WAIT=3600
# Port losses in an hour that a server is allowed.
MAX_LOSSES=6
# Recreations without a port before the server list is widened, and before the long wait.
WIDEN_AFTER=3
SLOW_AFTER=6

log() { echo "vpn watchdog: $*"; }
env_value() { [ ! -f "$ENV_FILE" ] || sed -n "s/^$1=//p" "$ENV_FILE" | tail -1; }
state() { [ ! -f "$STATE" ] || sed -n "s/^$1=//p" "$STATE" | tail -1; }

[ "$(env_value VPN_PORT_FORWARDING)" = on ] || exit 0

no_port_since="$(state no_port_since)"; no_port_since="${no_port_since:-0}"
port_since="$(state port_since)"; port_since="${port_since:-0}"
tries="$(state tries)"; tries="${tries:-0}"
widened_at="$(state widened_at)"; widened_at="${widened_at:-0}"
last_recreate="$(state last_recreate)"; last_recreate="${last_recreate:-0}"
recreations="$(state recreations)"; recreations="${recreations:-0}"

# $1: why. The three share Gluetun's network, so they go together; qBittorrent
# only while the media disk is there (the disk guard starts it otherwise).
recreate() {
  local services=(gluetun flaresolverr)
  ! mountpoint -q /data || services+=(qbittorrent)
  if [ "$widened_at" -gt 0 ]; then
    log "$1: recreating ${services[*]} on any server in the country"
    # An empty list in the environment overrides the one in .env.
    VPN_SERVER_HOSTNAMES="" docker compose --project-directory "$SERVER_DIR" up -d --force-recreate "${services[@]}" || log "the recreate failed"
  else
    log "$1: recreating ${services[*]}"
    docker compose --project-directory "$SERVER_DIR" up -d --force-recreate "${services[@]}" || log "the recreate failed"
  fi
  recreations=$((recreations + 1))
  last_recreate="$NOW"
  # The new container gets its own ten minutes.
  no_port_since="$NOW"
  port_since=0
}

# A recreate that is counted as a try: the server before it did not hold a port.
retry() {
  if [ "$tries" -ge "$WIDEN_AFTER" ] && [ "$widened_at" -eq 0 ] && [ -n "$(env_value VPN_SERVER_HOSTNAMES)" ]; then
    widened_at="$NOW"
  fi
  recreate "$1"
  tries=$((tries + 1))
}

# Gives qBittorrent the forwarded port when it listens on another. Gluetun gives
# it only when forwarding comes up: a qBittorrent that restarted alone, or did
# not answer then, is left without it. Its Web UI is Gluetun's localhost; when
# it does not answer, nothing is done.
sync_port() {
  local qbittorrent="http://127.0.0.1:8080/api/v2/app" forwarded listening
  forwarded="$(timeout 10 docker exec gluetun cat /tmp/gluetun/forwarded_port 2>/dev/null | head -1 || true)"
  listening="$(timeout 10 docker exec gluetun wget -qO- "$qbittorrent/preferences" 2>/dev/null \
    | sed -n 's/.*"listen_port":\([0-9]*\).*/\1/p' | head -1 || true)"
  [[ "$forwarded" =~ ^[0-9]+$ && "$listening" =~ ^[0-9]+$ ]] || return 0
  [ "$forwarded" != "$listening" ] || return 0
  if timeout 10 docker exec gluetun wget -qO- --post-data "json={\"listen_port\":$forwarded}" "$qbittorrent/setPreferences" >/dev/null 2>&1; then
    log "qBittorrent listened on port $listening, the forwarded port is $forwarded: set"
  else
    log "qBittorrent listened on port $listening, the forwarded port is $forwarded: it did not take it"
  fi
}

# The latest 04:00 that has passed.
four="$(date -d "$(date -d "@$NOW" +%F) 04:00" +%s)"
[ "$four" -le "$NOW" ] || four=$((four - 86400))

wait="$WAIT"
[ "$tries" -lt "$SLOW_AFTER" ] || wait="$LONG_WAIT"

if [ "$widened_at" -gt 0 ] && [ "$widened_at" -lt "$four" ]; then
  widened_at=0
  tries=0
  recreate "back to the servers named in .env"
elif timeout 10 docker exec gluetun test -s /tmp/gluetun/forwarded_port; then
  no_port_since=0
  [ "$port_since" -gt 0 ] || port_since="$NOW"
  [ $((NOW - port_since)) -lt "$WAIT" ] || tries=0
  # Gluetun writes this line each time it loses the port.
  losses="$(timeout 20 docker logs --since 1h gluetun 2>&1 | grep -c 'clearing port file' || true)"
  if [ "$losses" -gt "$MAX_LOSSES" ] && [ $((NOW - last_recreate)) -ge "$wait" ]; then
    retry "the port was lost $losses times in an hour"
  else
    sync_port
  fi
else
  port_since=0
  [ "$no_port_since" -gt 0 ] || no_port_since="$NOW"
  if [ $((NOW - no_port_since)) -ge "$wait" ]; then
    retry "no forwarded port for $(((NOW - no_port_since) / 60)) minutes"
  fi
fi

# Written beside the file and moved over it, so a reader never sees half of one.
# 644: the node exporter does not run as root.
write() {
  local tmp
  mkdir -p "$(dirname "$1")"
  tmp="$(mktemp "$1.XXXXXX")"
  cat > "$tmp"
  chmod 644 "$tmp"
  mv "$tmp" "$1"
}

write "$STATE" <<EOF
no_port_since=$no_port_since
port_since=$port_since
tries=$tries
widened_at=$widened_at
last_recreate=$last_recreate
recreations=$recreations
EOF

widened=0
[ "$widened_at" -eq 0 ] || widened=1
write "$TEXTFILE_DIR/server_vpn_watchdog.prom" <<EOF
server_vpn_watchdog_recreations_total $recreations
server_vpn_watchdog_widened $widened
EOF
