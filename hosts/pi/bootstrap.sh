#!/usr/bin/env bash
# ==============================================================================
# Raspberry Pi 5 (lemonpi) bootstrap: turns a fresh Raspberry Pi OS Lite card
# into the house DNS. Safe to rerun: every step checks before it acts.
#
#   sudo hosts/pi/bootstrap.sh
#
# Left to a human: `sudo tailscale up`, and the Pi-hole password in hosts/pi/.env.
# ==============================================================================
set -euo pipefail

PI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_USER="${SUDO_USER:-$(stat -c %U "$PI_DIR")}"
CONFIG_TXT=/boot/firmware/config.txt
CODENAME="$(sed -n 's/^VERSION_CODENAME=//p' /etc/os-release)"

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

step() { printf '\n==> %s\n' "$*"; }

# A reboot is pending until Wi-Fi is gone and log2ram runs, whichever run made the change.
finish() {
  if [ -e /sys/class/net/wlan0 ] || ! systemctl is-active --quiet log2ram; then
    step "Reboot to apply the Wi-Fi/Bluetooth and log2ram changes: sudo reboot"
  fi
  if ! tailscale status >/dev/null 2>&1; then
    echo "Tailscale isn't logged in yet: run sudo tailscale up"
  fi
}

step "Packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get full-upgrade -y -q
apt-get install -y -q git curl ca-certificates unattended-upgrades dnsutils

step "Unattended security upgrades"
AUTO_UPGRADES=/etc/apt/apt.conf.d/20auto-upgrades
if ! grep -qs 'APT::Periodic::Unattended-Upgrade "1"' "$AUTO_UPGRADES"; then
  printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' > "$AUTO_UPGRADES"
fi

step "Wi-Fi and Bluetooth off (the Pi is wired)"
ensure_overlay() {
  if ! grep -qxF "dtoverlay=$1" "$CONFIG_TXT"; then
    printf '\n[all]\ndtoverlay=%s\n' "$1" >> "$CONFIG_TXT"
  fi
}
ensure_overlay disable-wifi
ensure_overlay disable-bt
if systemctl is-enabled --quiet wpa_supplicant 2>/dev/null; then
  systemctl disable --now wpa_supplicant
fi

step "Fixed IP 192.168.1.35, with DNS that doesn't depend on this Pi-hole"
# Pi-hole is the house DHCP server, so the Pi can't lease its own address.
ETH_CON="$(nmcli -g GENERAL.CONNECTION device show eth0)"
if [ "$(nmcli -g ipv4.method con show "$ETH_CON")" != "manual" ] \
  || [ "$(nmcli -g ipv4.dns con show "$ETH_CON")" != "1.1.1.1,9.9.9.9" ]; then
  nmcli con mod "$ETH_CON" ipv4.method manual ipv4.addresses 192.168.1.35/24 \
    ipv4.gateway 192.168.1.1 ipv4.dns "1.1.1.1 9.9.9.9" ipv4.ignore-auto-dns yes
  nmcli device reapply eth0
fi

step "log2ram (logs in RAM, synced to the SD card daily)"
if ! dpkg -s log2ram >/dev/null 2>&1; then
  curl -fsSL https://azlux.fr/repo.gpg -o /usr/share/keyrings/azlux-archive-keyring.gpg
  echo "deb [signed-by=/usr/share/keyrings/azlux-archive-keyring.gpg] http://packages.azlux.fr/debian/ $CODENAME main" \
    > /etc/apt/sources.list.d/azlux.list
  apt-get update -q
  apt-get install -y -q log2ram
fi
sed -i 's/^SIZE=.*/SIZE=128M/' /etc/log2ram.conf

step "Docker"
if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $CODENAME stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
DAEMON_JSON=/etc/docker/daemon.json
DAEMON_CONFIG='{ "log-driver": "journald" }'
if [ "$(cat "$DAEMON_JSON" 2>/dev/null)" != "$DAEMON_CONFIG" ]; then
  echo "$DAEMON_CONFIG" > "$DAEMON_JSON"
  systemctl restart docker
fi
if ! id -nG "$TARGET_USER" | grep -qw docker; then
  usermod -aG docker "$TARGET_USER"
fi

step "Tailscale"
if ! command -v tailscale >/dev/null 2>&1; then
  curl -fsSL https://tailscale.com/install.sh | sh
fi
# The tailnet's DNS is this Pi-hole; the Pi itself must not depend on it.
tailscale set --accept-dns=false
# Subnet router: tailnet devices reach 192.168.1.x (and *.spicy-llama.duckdns.org) through
# this always-on Pi. The route still has to be approved once in the Tailscale admin console.
SYSCTL_CONF=/etc/sysctl.d/99-tailscale.conf
if ! grep -qs 'net.ipv4.ip_forward = 1' "$SYSCTL_CONF"; then
  printf 'net.ipv4.ip_forward = 1\nnet.ipv6.conf.all.forwarding = 1\n' > "$SYSCTL_CONF"
  sysctl -p /etc/sysctl.d/99-tailscale.conf
fi
tailscale set --advertise-routes=192.168.1.0/24

step "Pi-hole + Unbound"
if [ ! -f "$PI_DIR/.env" ]; then
  cp "$PI_DIR/.env.example" "$PI_DIR/.env"
  chown "$TARGET_USER:" "$PI_DIR/.env"
  chmod 600 "$PI_DIR/.env"
  echo "Created $PI_DIR/.env. Set PIHOLE_PASSWORD in it, then rerun this script."
  finish
  exit 0
fi
docker compose --project-directory "$PI_DIR" up -d --remove-orphans

step "Done."
finish
