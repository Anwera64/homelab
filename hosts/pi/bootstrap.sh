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
REBOOT_NEEDED=0
CODENAME="$(sed -n 's/^VERSION_CODENAME=//p' /etc/os-release)"

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

step() { printf '\n==> %s\n' "$*"; }

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
    REBOOT_NEEDED=1
  fi
}
ensure_overlay disable-wifi
ensure_overlay disable-bt

step "log2ram (logs in RAM, synced to the SD card daily)"
if ! dpkg -s log2ram >/dev/null 2>&1; then
  curl -fsSL https://azlux.fr/repo.gpg -o /usr/share/keyrings/azlux-archive-keyring.gpg
  echo "deb [signed-by=/usr/share/keyrings/azlux-archive-keyring.gpg] http://packages.azlux.fr/debian/ $CODENAME main" \
    > /etc/apt/sources.list.d/azlux.list
  apt-get update -q
  apt-get install -y -q log2ram
  REBOOT_NEEDED=1
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

step "Pi-hole + Unbound"
if [ ! -f "$PI_DIR/.env" ]; then
  cp "$PI_DIR/.env.example" "$PI_DIR/.env"
  chown "$TARGET_USER:" "$PI_DIR/.env"
  chmod 600 "$PI_DIR/.env"
  echo "Created $PI_DIR/.env. Set PIHOLE_PASSWORD in it, then rerun this script."
  exit 0
fi
docker compose --project-directory "$PI_DIR" up -d --remove-orphans

if [ "$REBOOT_NEEDED" -eq 1 ]; then
  step "Done. Reboot to apply the Wi-Fi/Bluetooth and log2ram changes: sudo reboot"
else
  step "Done."
fi
if ! tailscale status >/dev/null 2>&1; then
  echo "Tailscale isn't logged in yet: run sudo tailscale up"
fi
