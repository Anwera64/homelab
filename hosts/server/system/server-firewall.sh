#!/usr/bin/env bash
# ==============================================================================
# Server firewall. Two layers, because Docker forwards a published port before
# the host's own input rules ever see the packet:
#
#   1. DOCKER-USER: who may reach the published service ports. Only the
#      addresses in SERVER_ALLOWED_SOURCES (hosts/server/.env), lemonpi by default.
#   2. An input policy for the host itself: SSH for the LAN, nothing else. This
#      also covers IPv6, where published ports are reached through the host.
#
#   server-firewall.sh             apply (what the systemd unit runs)
#   server-firewall.sh --try       apply, and undo after two minutes unless confirmed
#   server-firewall.sh --confirm   keep what --try applied
#   server-firewall.sh --clear     remove every rule
# ==============================================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Traffic arriving from the LAN, wired or wireless. The containers' own traffic
# comes in on Docker's bridges and is left alone.
LAN_IFACES=(en+ wl+)

allowed_sources() {
  local sources
  sources="$(sed -n 's/^SERVER_ALLOWED_SOURCES=//p' "$SERVER_DIR/.env" 2>/dev/null | tail -1 | tr -d '"')"
  echo "${sources:-192.168.1.35}"
}

clear_rules() {
  iptables -F DOCKER-USER 2>/dev/null || true
  nft delete table inet server_host 2>/dev/null || true
}

apply_rules() {
  local iface source

  iptables -N DOCKER-USER 2>/dev/null || true
  iptables -F DOCKER-USER
  iptables -A DOCKER-USER -m conntrack --ctstate RELATED,ESTABLISHED -j RETURN
  for iface in "${LAN_IFACES[@]}"; do
    for source in $(allowed_sources); do
      iptables -A DOCKER-USER -i "$iface" -s "$source" -j RETURN
    done
  done
  for iface in "${LAN_IFACES[@]}"; do
    iptables -A DOCKER-USER -i "$iface" -j DROP
  done

  nft -f - <<'EOF'
table inet server_host
delete table inet server_host
table inet server_host {
  chain input {
    type filter hook input priority 0; policy drop;
    iif lo accept
    ct state established,related accept
    ct state invalid drop
    meta l4proto { icmp, ipv6-icmp } accept
    udp dport 68 accept
    udp dport 546 accept
    iifname "docker0" accept
    iifname "br-*" accept
    ip saddr 192.168.1.0/24 tcp dport 22 accept
    ip6 saddr { fe80::/10, fc00::/7 } tcp dport 22 accept
  }
}
EOF
}

case "${1:-}" in
  --clear)
    clear_rules
    echo "Firewall rules removed."
    ;;
  --try)
    systemctl stop server-firewall-rollback.timer 2>/dev/null || true
    systemd-run --on-active=120 --unit=server-firewall-rollback "$(readlink -f "$0")" --clear
    apply_rules
    echo "Applied. Open a new SSH session and check it; run '$0 --confirm' within two minutes to keep it."
    ;;
  --confirm)
    systemctl stop server-firewall-rollback.timer
    echo "Kept."
    ;;
  "")
    apply_rules
    ;;
  *)
    echo "Unknown option: $1" >&2
    exit 1
    ;;
esac
