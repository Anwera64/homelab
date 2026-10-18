#!/usr/bin/env bash
# ==============================================================================
# Holds Docker back at boot until the host has a DNS server. Runs before dockerd
# (docker.service.d/10-wait-for-dns.conf).
#
# On Wi-Fi, network-online.target is reached before the DHCP lease, so Docker can
# start while /etc/resolv.conf names no server. Every container that uses
# Docker's own DNS then gets none, and keeps none until it is recreated: on
# 2026-10-18 Seerr showed an empty page and Jellyfin found no metadata.
#
# Gives up after SERVER_DNS_WAIT_SECONDS and lets Docker start anyway: a stack
# without outside names is better than no stack. docker.service has no start
# timeout of its own, so the limit is here.
# ==============================================================================
set -euo pipefail

RESOLV_CONF="${RESOLV_CONF:-/etc/resolv.conf}"
LIMIT="${SERVER_DNS_WAIT_SECONDS:-120}"

has_nameserver() {
  grep -q '^[[:space:]]*nameserver[[:space:]]' "$RESOLV_CONF" 2>/dev/null
}

for ((i = 0; i < LIMIT; i++)); do
  has_nameserver && exit 0
  sleep 1
done
has_nameserver && exit 0
echo "wait for DNS: no nameserver after $LIMIT s; starting Docker anyway" >&2
