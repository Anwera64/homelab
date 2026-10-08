#!/usr/bin/env bash
# ==============================================================================
# Host metrics. The numbers about this machine that no exporter reports, left
# as files for the node exporter (the server dashboard's Temperatures, Disks
# and Updates sections):
#
#   server_host.prom   when unattended upgrades last ran, whether the media disk
#                      is mounted, whether a reboot is waiting, how long the CPU
#                      has been slowed down for heat
#   server_smart.prom  each disk's latest self-test: passed or not, and the
#                      disk's power-on hours when it ran
#   server_qbittorrent.prom  what qBittorrent has downloaded and uploaded, its
#                      peers and whether they can reach it (the Downloads dashboard)
#
# Runs every minute from server-metrics.timer, as root (smartctl needs it).
# ==============================================================================
set -euo pipefail

TEXTFILE_DIR="${SERVER_TEXTFILE_DIR:-/var/lib/node_exporter/textfile}"
APT_STAMP="${SERVER_APT_STAMP:-/var/lib/apt/periodic/unattended-upgrades-stamp}"
REBOOT_FLAG="${SERVER_REBOOT_FLAG:-/run/reboot-required}"
CPU_THROTTLE_FILE="${SERVER_CPU_THROTTLE_FILE:-/sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_total_time_ms}"
SMART_PROM="$TEXTFILE_DIR/server_smart.prom"

# Writes stdin to $1. Written beside the file and moved over it, so the exporter
# never reads half of one. 644: the exporter does not run as root.
write_prom() {
  local tmp
  tmp="$(mktemp "$1.XXXXXX")"
  cat > "$tmp"
  chmod 644 "$tmp"
  mv "$tmp" "$1"
}

flag() { if "$@"; then echo 1; else echo 0; fi; }

mkdir -p "$TEXTFILE_DIR"

{
  # apt touches the stamp after each unattended-upgrades run.
  [ ! -e "$APT_STAMP" ] || echo "server_unattended_upgrade_last_run_timestamp_seconds $(stat -c %Y "$APT_STAMP")"
  echo "server_media_disk_mounted $(flag mountpoint -q /data)"
  echo "server_reboot_required $(flag test -e "$REBOOT_FLAG")"
  # The kernel counts, in milliseconds since boot, how long the CPU was slowed down for heat.
  if [ -r "$CPU_THROTTLE_FILE" ]; then
    ms="$(<"$CPU_THROTTLE_FILE")"
    printf 'server_cpu_throttled_seconds_total %d.%03d\n' "$((ms / 1000))" "$((ms % 1000))"
  fi
} | write_prom "$TEXTFILE_DIR/server_host.prom"

# qBittorrent has Gluetun's network, and its Web UI lets Gluetun's localhost in
# without a login while port forwarding is on. When it does not answer (a login
# is asked for, or Gluetun is stopped) only "up 0" is written.
{
  state="$(timeout 10 docker exec gluetun wget -qO- http://127.0.0.1:8080/api/v2/sync/maindata 2>/dev/null \
    | jq -c '.server_state | select(.alltime_dl != null)' 2>/dev/null || true)"
  if [ -n "$state" ]; then
    echo "server_qbittorrent_up 1"
    # All-time counters, so a restart of qBittorrent does not start them again.
    # "connected" means other peers can reach its port; "firewalled" that they cannot.
    jq -r '
      "server_qbittorrent_downloaded_bytes_total \(.alltime_dl)",
      "server_qbittorrent_uploaded_bytes_total \(.alltime_ul)",
      "server_qbittorrent_connectable \(if .connection_status == "connected" then 1 else 0 end)",
      "server_qbittorrent_peer_connections \(.total_peer_connections)"
    ' <<<"$state"
  else
    echo "server_qbittorrent_up 0"
  fi
} | write_prom "$TEXTFILE_DIR/server_qbittorrent.prom"

# The latest self-test of one disk, as "passed hours", or nothing when it has
# never run one. ATA disks say passed or not; NVMe gives a result code, 0 for passed.
selftest() {
  jq -r '
    (.ata_smart_self_test_log.standard.table[0] // empty | "\(if .status.passed then 1 else 0 end) \(.lifetime_hours)"),
    (.nvme_self_test_log.table[0] // empty | "\(if .self_test_result.value == 0 then 1 else 0 end) \(.power_on_hours)")
  ' | head -1
}

{
  # "sda", "nvme0": the names the SMART exporter gives the disks.
  for device in $(smartctl --scan | awk '{print $1}'); do
    name="${device#/dev/}"
    # -n standby: a sleeping disk is left asleep, as smartd leaves it. smartctl's
    # exit status is a set of bits; 2 is "asleep, or could not be opened".
    status=0
    json="$(smartctl -n standby -l selftest -j "$device")" || status=$?
    read -r passed hours < <(selftest <<<"$json" 2>/dev/null; echo) || true
    if [ -n "$passed" ]; then
      echo "server_smart_selftest_passed{device=\"$name\"} $passed"
      echo "server_smart_selftest_power_on_hours{device=\"$name\"} $hours"
    elif (( status & 2 )); then
      # Its last answer stands.
      grep -F "{device=\"$name\"}" "$SMART_PROM" 2>/dev/null || true
    fi
  done
} | write_prom "$SMART_PROM"
