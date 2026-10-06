#!/usr/bin/env bash
# ==============================================================================
# Server bootstrap: turns a fresh Debian 13 install (SSH server + standard
# utilities, no desktop) into the always-on app server. Safe to rerun: every
# step checks before it acts.
#
#   sudo hosts/server/bootstrap.sh              # host setup, then the stack
#   sudo hosts/server/bootstrap.sh --no-stack   # host setup only
#
# Left to a human: the secrets in hosts/server/.env, and the reboots it asks for.
# ==============================================================================
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_USER="${SUDO_USER:-$(stat -c %U "$SERVER_DIR")}"
USER_HOME="$(getent passwd "$TARGET_USER" | cut -d: -f6)"
CODENAME="$(sed -n 's/^VERSION_CODENAME=//p' /etc/os-release)"
# What the Hub embeds with (EMBEDDING_MODEL in docker-compose.yml).
EMBEDDING_MODEL="bge-m3"
NO_STACK=0
ENV_CREATED=0
REBOOT_PENDING=0

for arg in "$@"; do
  case "$arg" in
    --no-stack) NO_STACK=1 ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

step() { printf '\n==> %s\n' "$*"; }

# Writes stdin to $1 only when the content differs. Succeeds when it wrote.
write_if_changed() {
  local target="$1" tmp
  tmp="$(mktemp)"
  cat > "$tmp"
  if cmp -s "$tmp" "$target" 2>/dev/null; then
    rm -f "$tmp"
    return 1
  fi
  install -D -m "${2:-0644}" "$tmp" "$target"
  rm -f "$tmp"
}

# Writes a unit from system/ with this checkout's path filled in. Succeeds when it changed.
place_unit() {
  sed "s|@SERVER_DIR@|$SERVER_DIR|g" "$SERVER_DIR/system/$1" | write_if_changed "/etc/systemd/system/$1"
}

# Places a unit and starts it.
install_unit() {
  if place_unit "$1"; then
    systemctl daemon-reload
    systemctl enable --now "$1"
    systemctl restart "$1"
  else
    systemctl enable --now "$1"
  fi
}

finish() {
  if [ "$REBOOT_PENDING" -eq 1 ] || [ -e /run/reboot-required ]; then
    step "Reboot to finish, then rerun this script: sudo reboot"
  fi
  if [ -e /etc/sudoers.d/90-setup-temp ]; then
    echo "The temporary passwordless sudo rule is still in place: /etc/sudoers.d/90-setup-temp."
    echo "Remove it once the setup is done."
  fi
}

step "Settings file"
if [ ! -f "$SERVER_DIR/.env" ]; then
  cp "$SERVER_DIR/.env.example" "$SERVER_DIR/.env"
  chown "$TARGET_USER:" "$SERVER_DIR/.env"
  chmod 600 "$SERVER_DIR/.env"
  ENV_CREATED=1
  echo "Created $SERVER_DIR/.env. The host setup continues; fill in the secrets before the stack can start."
fi
env_value() { sed -n "s/^$1=//p" "$SERVER_DIR/.env" | tail -1; }

step "Packages"
export DEBIAN_FRONTEND=noninteractive
# The NVIDIA driver and its firmware live in contrib and non-free.
if ! grep -qE '^deb .* non-free( |$)' /etc/apt/sources.list; then
  sed -i -E '/^deb(-src)? / { / contrib/! s/ main/ main contrib/; / non-free( |$)/! s/ non-free-firmware/ non-free non-free-firmware/ }' /etc/apt/sources.list
fi
apt-get update -q
apt-get full-upgrade -y -q
apt-get install -y -q git curl ca-certificates gnupg jq rsync rfkill iptables nftables unattended-upgrades

step "Unattended upgrades (Debian security and Docker), rebooting at 05:00 when needed"
AUTO_UPGRADES=/etc/apt/apt.conf.d/20auto-upgrades
if ! grep -qs 'APT::Periodic::Unattended-Upgrade "1"' "$AUTO_UPGRADES"; then
  printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' > "$AUTO_UPGRADES"
fi
write_if_changed /etc/apt/apt.conf.d/52server-upgrades <<'EOF' || true
Unattended-Upgrade::Origins-Pattern { "origin=Docker"; };
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "05:00";
EOF

step "Radios on (they can come up in airplane mode after the install)"
if grep -qx 1 /sys/class/rfkill/rfkill*/soft 2>/dev/null; then
  rfkill unblock all
fi

step "Docker"
if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $CODENAME stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
if ! id -nG "$TARGET_USER" | grep -qw docker; then
  usermod -aG docker "$TARGET_USER"
fi

# >>> laptop ==================================================================
# Everything specific to the Lenovo Legion Y540 and its GTX 1660 Ti. On other
# hardware this section is what changes; the rest of the script stays.

step "NVIDIA driver"
# The open-source nouveau driver hangs this GPU when it wakes it, which freezes
# lspci and the driver install. It has to be gone, and the machine rebooted,
# before the NVIDIA packages go in.
if printf 'blacklist nouveau\noptions nouveau modeset=0\n' | write_if_changed /etc/modprobe.d/blacklist-nouveau.conf; then
  update-initramfs -u
fi
if lsmod | grep -q '^nouveau'; then
  REBOOT_PENDING=1
  echo "nouveau is still loaded. Reboot, then rerun this script to install the NVIDIA driver."
  finish
  exit 0
fi
if ! dpkg -s nvidia-kernel-dkms >/dev/null 2>&1; then
  apt-get install -y -q linux-headers-amd64 nvidia-kernel-dkms nvidia-smi libnvidia-encode1 libnvcuvid1 firmware-misc-nonfree
fi
if [ ! -d /sys/module/nvidia ]; then
  REBOOT_PENDING=1
fi

step "NVIDIA Container Toolkit (the GPU inside containers)"
if ! command -v nvidia-ctk >/dev/null 2>&1; then
  curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey \
    | gpg --batch --yes --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
  curl -fsSL https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list \
    | sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' \
    > /etc/apt/sources.list.d/nvidia-container-toolkit.list
  apt-get update -q
  apt-get install -y -q nvidia-container-toolkit
fi
if ! jq -e '.runtimes.nvidia' /etc/docker/daemon.json >/dev/null 2>&1; then
  nvidia-ctk runtime configure --runtime=docker
  systemctl restart docker
fi

step "Lid closed changes nothing; the panel goes dark after a minute"
if printf '[Login]\nHandleLidSwitch=ignore\nHandleLidSwitchExternalPower=ignore\nHandleLidSwitchDocked=ignore\n' \
  | write_if_changed /etc/systemd/logind.conf.d/lid.conf; then
  systemctl restart systemd-logind
fi
if ! grep -q consoleblank /etc/default/grub; then
  sed -i -E 's/^(GRUB_CMDLINE_LINUX_DEFAULT=")([^"]*)"/\1\2 consoleblank=60"/' /etc/default/grub
  update-grub
  REBOOT_PENDING=1
fi

step "Battery: held at 60%, clean shutdown when a power cut drains it"
install_unit server-battery.service

# <<< laptop ==================================================================

step "Docker logs capped (Loki holds the history)"
DAEMON_JSON=/etc/docker/daemon.json
LOG_CONFIG='{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }'
CURRENT_CONFIG="$(cat "$DAEMON_JSON" 2>/dev/null || echo '{}')"
# Merged, never replaced: the file also holds the GPU runtime entry.
if jq -S --argjson log "$LOG_CONFIG" '. + $log' <<<"$CURRENT_CONFIG" | write_if_changed "$DAEMON_JSON"; then
  systemctl restart docker
fi

step "SSH: keys only"
if [ -s "$USER_HOME/.ssh/authorized_keys" ]; then
  if printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin no\n' \
    | write_if_changed /etc/ssh/sshd_config.d/10-keys-only.conf; then
    sshd -t
    systemctl reload ssh
  fi
else
  echo "$TARGET_USER has no authorized SSH key yet, so password logins stay on. Add a key and rerun."
fi

step "Firewall: service ports for the allowed addresses only, SSH for the LAN"
install_unit server-firewall.service

step "Media disk at /data"
mkdir -p /data
# While nothing is mounted the folder is immutable, so a container started
# without the disk fails instead of quietly filling the SSD.
mountpoint -q /data || chattr +i /data
DATA_DISK_UUID="$(env_value DATA_DISK_UUID)"
if [ -n "$DATA_DISK_UUID" ]; then
  if ! grep -q '[[:space:]]/data[[:space:]]' /etc/fstab; then
    echo "UUID=$DATA_DISK_UUID /data ext4 defaults,noatime,nofail,x-systemd.device-timeout=30s 0 2" >> /etc/fstab
    systemctl daemon-reload
  fi
  mountpoint -q /data || mount /data || echo "The media disk is not connected."
else
  echo "DATA_DISK_UUID is empty in .env: no media disk is mounted."
fi
if mountpoint -q /data; then
  # TRaSH layout; the apps run as 1000:1000.
  install -d -o 1000 -g 1000 /data/media /data/media/movies /data/media/tv /data/torrents
fi

step "Disk guard: media services run only while the disk is there"
install_unit server-media.service

step "Nightly update at 04:00: what was merged on GitHub reaches the stack"
# The timer starts the service; the service itself is never enabled.
place_unit server-update.service && systemctl daemon-reload
install_unit server-update.timer

step "App settings folders"
# Created here, owned by the apps' user: Docker would create a missing one as
# root, and the apps that run as user 1000 could not write to it.
CONFIG_PATH="$(env_value CONFIG_PATH)"
APP_FOLDERS=(qbittorrent prowlarr radarr sonarr bazarr seerr jellyfin jellyfin/config jellyfin/cache maintainerr cleanuparr recyclarr)
for app in "${APP_FOLDERS[@]}"; do
  [ -d "$CONFIG_PATH/$app" ] || install -d -o 1000 -g 1000 "$CONFIG_PATH/$app"
done

step "Stack"
if [ "$NO_STACK" -eq 1 ]; then
  echo "Skipped (--no-stack)."
elif [ "$ENV_CREATED" -eq 1 ]; then
  echo "Fill in $SERVER_DIR/.env, then rerun this script to start the stack."
else
  "$SERVER_DIR/system/server-stack-up.sh"

  step "Embedding model ($EMBEDDING_MODEL)"
  # Ollama takes a moment to answer after it starts.
  for _ in $(seq 1 30); do
    docker exec ollama ollama list >/dev/null 2>&1 && break
    sleep 1
  done
  if docker exec ollama ollama list 2>/dev/null | grep -q "^${EMBEDDING_MODEL}[:[:space:]]"; then
    echo "Already there."
  else
    # Without it the Hub still answers: a turn ranks what it read by keywords.
    docker exec ollama ollama pull "$EMBEDDING_MODEL" || echo "The pull failed; rerun this script to try again."
  fi
fi

step "Done."
finish
