# lemonpi: the house DNS, dashboard and HTTPS entry point

A Raspberry Pi 5 (1 GB RAM) on Raspberry Pi OS Lite 64-bit (Debian 13 trixie), wired on `eth0` at the reserved IP `192.168.1.35`. It answers DNS for every device on the LAN and the tailnet.

## What runs here

| Piece | What it does |
| --- | --- |
| Pi-hole v6 (Docker) | DNS, ad blocking and DHCP. Admin page: https://pihole.spicy-llama.duckdns.org/admin or http://192.168.1.35:8081/admin |
| Caddy (Docker) | HTTPS for `*.spicy-llama.duckdns.org` (DuckDNS points here), wildcard cert via the DuckDNS DNS challenge. Pi-hole, Homepage, Uptime Kuma and ntfy are served locally; everything else goes to the ports the server's containers publish on 192.168.1.30 and returns 502 while it's off. Image built by CI (`.github/workflows/caddy-image.yml`). |
| Unbound (Docker) | Pi-hole's only upstream, on `127.0.0.1:5335`. Recursive from the root servers, DNSSEC on. |
| Homepage (Docker) | The homelab dashboard at https://home.spicy-llama.duckdns.org. Plain http://192.168.1.35 redirects there; http://192.168.1.35:3000 is the container's own port, for when HTTPS is down. The server's services are checked over HTTP on 192.168.1.30; its config is `config/homepage` in this repo. |
| Uptime Kuma (Docker) | Asks each of the server's services whether it answers, every minute, keeps the history and sends an alert when one stops. It runs here so that the server's own outage is reported. https://uptime.spicy-llama.duckdns.org |
| ntfy (Docker) | Pushes Uptime Kuma's alerts to the phones. Nobody publishes or subscribes without a login. https://ntfy.spicy-llama.duckdns.org |
| Tailscale (native) | Subnet router for `192.168.1.0/24`, so remote devices reach the LAN, and ad blocking away from home. |
| log2ram | Keeps `/var/log` in RAM and syncs it to the card daily. |
| unattended-upgrades | Installs Debian security updates by itself. |

Wi-Fi and Bluetooth are disabled (the Pi is wired). To save the SD card, Pi-hole writes its query database hourly and keeps 30 days, its logs live in tmpfs, and Docker logs go to journald, which sits under log2ram. Uptime Kuma is the heaviest writer: measured with 16 monitors it writes about 1 GB a day and holds about 125 MB of memory, and ntfy 17 MB. The card is a SanDisk High Endurance, rated for roughly 56 TB.

## Fresh card setup

1. Flash Raspberry Pi OS Lite (64-bit) with Raspberry Pi Imager. Set hostname `lemonpi`, user `anwera97`, and SSH with your public key. No Wi-Fi needed.
2. In the router, reserve `192.168.1.35` for the Pi's `eth0` MAC address.
3. On the Pi (the repo is public; only `hosts/pi` and `config/homepage` are checked out, and bootstrap adds the second):

```sh
sudo apt-get update && sudo apt-get install -y git
git clone --depth 1 --filter=blob:none --sparse https://github.com/Anwera64/homelab.git ~/homelab
cd ~/homelab && git sparse-checkout set hosts/pi
sudo hosts/pi/bootstrap.sh          # first run creates hosts/pi/.env and stops
openssl rand -base64 18             # put this in PIHOLE_PASSWORD in hosts/pi/.env
sudo hosts/pi/bootstrap.sh
sudo reboot
sudo tailscale up                   # open the printed URL to log in
```

4. In the Tailscale admin console: disable key expiry for `lemonpi`, and under **Edit route settings** approve the `192.168.1.0/24` subnet route. With that route, tailnet devices away from home reach the whole LAN through the Pi, including `*.spicy-llama.duckdns.org`, which resolves to the desktop's LAN IP. Under DNS, add lemonpi's tailnet IP as a global nameserver and turn on "Override local DNS".
5. Fill `DUCKDNS_TOKEN` and the Homepage API keys in `hosts/pi/.env` (each app shows its key in its own settings) and rerun bootstrap. `MAINTAINERR_USER` and `MAINTAINERR_PASSWORD_HASH` are required too: Caddy asks for them on Maintainerr's name, and the stack does not start without them (`.env.example` shows how to make the hash). Point the DuckDNS record at 192.168.1.35.. Switch DHCP over to Pi-hole (next section).

## DHCP (Pi-hole replaces the Livebox's)

The Livebox 7 won't let you change the DNS it hands out, so Pi-hole serves DHCP instead. It uses the same range (.10–.150), the Livebox (.1) as the gateway, and 24h leases. The Pi gives itself a fixed 192.168.1.35 and uses 1.1.1.1/9.9.9.9 for its own lookups, so it never depends on its own Pi-hole.

Static leases live only in `hosts/pi/.env` (`PIHOLE_DHCP_HOSTS`, `;`-separated `MAC,IP,name`), because MACs don't belong in a public repo. They're read-only in the web UI: to add one, edit `.env` and rerun bootstrap.

Switching over:
1. Put the Livebox reservations into `PIHOLE_DHCP_HOSTS`.
2. Livebox → advanced settings → network configuration → DHCP → **deactivate** → save.
3. Straight away, set `PIHOLE_DHCP_ACTIVE=true` in `.env` and run `sudo hosts/pi/bootstrap.sh`.
4. Reconnect a device and check it got DNS 192.168.1.35. The rest move over as their leases renew.

Rollback, which also works if the Pi is dead: activate DHCP on the Livebox again, then set `PIHOLE_DHCP_ACTIVE=false` and rerun.

## Updating

```sh
cd ~/homelab && git pull && sudo hosts/pi/bootstrap.sh
```

The script is safe to rerun. Image versions are pinned in `docker-compose.yml`; bump them in a PR.

## Uptime and alerts

Uptime Kuma checks the server's services and keeps the history. ntfy pushes its alerts to the phones. They are served at https://uptime.spicy-llama.duckdns.org and https://ntfy.spicy-llama.duckdns.org. They run on the Pi so that the server's own outage is reported. Nothing goes in `.env`: Kuma's monitors and settings are in `hosts/pi/data/uptime-kuma`, and ntfy's users are in `hosts/pi/data/ntfy`.

### First run

1. Open https://uptime.spicy-llama.duckdns.org right after the stack starts: the first visitor creates the admin account. Choose SQLite when it asks for the database.
2. Make ntfy's users on the Pi. Each command asks for a password, and the last prints a token starting with `tk_`.

```sh
docker exec -it ntfy ntfy user add --role=admin <you>     # your phone signs in as this user
docker exec -it ntfy ntfy user add kuma                   # Uptime Kuma publishes as this one
docker exec -it ntfy ntfy access kuma homelab write-only  # and may only write to the "homelab" topic
docker exec -it ntfy ntfy token add kuma                  # prints the token for the next step
```

3. In Uptime Kuma: Settings → Notifications → Setup Notification. Type ntfy, server URL `http://ntfy`, topic `homelab`, authentication by access token (the `tk_` one). Press Test. Turn on "Default enabled" so new monitors use it.
4. On each phone: install the ntfy app, set the default server to `https://ntfy.spicy-llama.duckdns.org`, sign in with the admin user and subscribe to `homelab`.

### Monitors to add

Add them by hand in the Uptime Kuma page. From here Kuma reaches the server's services on the ports they publish on 192.168.1.30. Container names only resolve inside the server's own stack, so do not use them.

| Monitor | Type | Address |
| --- | --- | --- |
| The server | Ping | `192.168.1.30` |
| Household Hub | HTTP | `http://192.168.1.30:3051/health` |
| Jellyfin | HTTP | `http://192.168.1.30:8096` |
| Seerr | HTTP | `http://192.168.1.30:5055` |
| Jellystat | HTTP | `http://192.168.1.30:3005` |
| Sonarr | HTTP | `http://192.168.1.30:8989` |
| Radarr | HTTP | `http://192.168.1.30:7878` |
| Prowlarr | HTTP | `http://192.168.1.30:9696` |
| Bazarr | HTTP | `http://192.168.1.30:6767` |
| Maintainerr | HTTP | `http://192.168.1.30:6246` |
| Cleanuparr | HTTP | `http://192.168.1.30:11011` |
| qBittorrent (through the VPN) | HTTP | `http://192.168.1.30:8080` |
| Grafana | HTTP | `http://192.168.1.30:3002` |
| Pi-hole's DNS | DNS | resolver `192.168.1.35` |
| ntfy | HTTP | `http://ntfy/v1/health` |
| The desktop's Ollama | HTTP | `http://192.168.1.20:11434` |

- The desktop is off by design at times, so leave the notification off on the Ollama monitor: it is there for the history.
- The media services stop while the media disk is unplugged, so their monitors alert then, which is the point.
- Loki, Prometheus, SearXNG and FlareSolverr publish no port, so they cannot be asked from here; Grafana's monitor is the nearest check on the log stack.

### Restarts that are not outages

The server's nightly update restarts its stack at 04:00 when a bump was merged, and a reboot for security updates can follow at 05:00. Two settings keep these from the phones:

- Under Maintenance, add a recurring maintenance window every day from 04:00 to 05:15, for all monitors. No alert is sent during it, so a real outage in that time alerts only once the window ends.
- On each monitor, set Retries to 3. A service that is back within three checks is then never reported, at any hour.

### What it cannot report

Uptime Kuma and ntfy stop with lemonpi, so when the Pi itself is down no alert is sent. DNS is down with it, so the house notices.

Away from home the phone reaches ntfy over Tailscale only, so an alert arrives when the phone is on the tailnet.

The Pi ships no logs to Loki, so their output is read on the Pi with `docker logs uptime-kuma` and `docker logs ntfy`.

## Checks

```sh
dig @127.0.0.1 -p 5335 example.com      # Unbound answers
dig @192.168.1.35 doubleclick.net       # 0.0.0.0 (blocked)
dig @192.168.1.35 dnssec-failed.org     # SERVFAIL (DNSSEC works)
docker compose --project-directory ~/homelab/hosts/pi ps   # all six up, Pi-hole and Unbound healthy
```

## Backups

- After changing lists, export from Pi-hole: Settings, Teleporter.
- `hosts/pi/data/` and `hosts/pi/.env` are not in git. Keep the Teleporter export and the password somewhere safe.
- Uptime Kuma's monitors and history are in `hosts/pi/data/uptime-kuma` and ntfy's users in `hosts/pi/data/ntfy`. Copy both folders to keep them.
