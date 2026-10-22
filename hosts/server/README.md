# server: the always-on app server

A Lenovo Legion Y540 (i7-9750HF, 16 GB, GTX 1660 Ti) on Debian 13 server, at the reserved address `192.168.1.30` (`server.lan`). It runs everything the desktop used to run except the chat model: the media server, the arr pipeline behind the VPN kill-switch, the Household Hub with its embedder, and the log stack.

## What is live

- **The Hub and the logs:** the Household Hub, SearXNG, Loki, Grafana and Alloy. An Ollama of its own runs the embedder (`bge-m3`) on the GTX 1660 Ti: it ranks what a chat turn reads. It unloads after 30 minutes without use, and if it is down the Hub ranks by keywords instead.
- **The media services:** Jellyfin, Seerr, Jellystat, Sonarr, Radarr, Prowlarr, Bazarr, Maintainerr, Cleanuparr, Recyclarr, and qBittorrent behind the Gluetun VPN. Their files are on the media disk at `/data`.
- **Routing:** lemonpi's Caddy sends every `https://` name except Pi-hole and the dashboard to this machine.
- **Staying on the desktop:** the chat model, in Ollama on the RTX 5080. The Hub reaches it over the LAN, so the chat model needs the desktop awake. The desktop also keeps an Alloy that ships its logs to this Loki.

## What runs here

| Piece | What it does |
| --- | --- |
| The stack (Docker) | The services in [`docker-compose.yml`](docker-compose.yml), each with a memory cap. The Hub asks the desktop's Ollama for the chat model and the stack's own Ollama for embeddings; the bootstrap script pulls `bge-m3` when it is missing. |
| Logs (Docker) | Loki keeps 30 days of every container's output, from this machine and the desktop. Grafana searches it at https://grafana.spicy-llama.duckdns.org. |
| Metrics (Docker) | Prometheus keeps a year of the machine's own numbers. Three exporters report them: the node exporter (CPU, memory, disks, network, temperatures, battery), the SMART exporter (disk health and the media disk's temperature) and the GPU exporter (the GTX 1660 Ti, through nvidia-smi). None of the four has a login, so each publishes no port: only Grafana and Prometheus inside the stack reach them. Prometheus also reads lemonpi's node exporter over the LAN, for the "lemonpi" dashboard (`hosts/pi/README.md`, Metrics). |
| Host metrics (systemd timer) | Once every minute, `server-metrics.sh` writes down what no exporter reports: when unattended upgrades last ran, whether the media disk is mounted, whether a reboot is waiting, how long the CPU has been slowed down for heat, and each disk's latest self-test. The nightly update writes when it ran and when it last applied something. The node exporter reads both from `/var/lib/node_exporter/textfile`. |
| Firewall | The published service ports answer only the addresses in `SERVER_ALLOWED_SOURCES`. SSH answers the LAN. |
| Disk guard | Stops the media services while the media disk is unplugged. When it is plugged back in, checks the filesystem, mounts it and starts them again. |
| smartd | Runs a short self-test on both disks every Sunday at 03:00. |
| Battery watcher | Holds the charge near 60%. In a power cut it stops the containers and powers off at 10%. |
| Wi-Fi band (systemd timer) | Once every minute, moves the Wi-Fi back to 5 GHz when the card has settled on 2.4 GHz. |
| unattended-upgrades | Installs Debian security updates and Docker updates, and reboots at 05:00 when one needs it. |
| Wait for DNS (Docker drop-in) | At boot, holds Docker back until the Wi-Fi's DHCP lease has written a DNS server, for up to two minutes. Containers keep the DNS servers they started with: started too early, every one without `dns:` in the compose file cannot look up outside names until it is recreated. |
| Renovate (Docker, systemd timer) | Runs every hour: opens the version bump PRs on GitHub and merges the ones that may merge themselves once CI is green. |
| Nightly update (systemd timer) | At 04:00, brings in what was merged on GitHub and restarts the stack when something changed. |

## Fresh install

1. In the BIOS (F2): Secure Boot **off**, so the NVIDIA module loads without signing.
2. Install Debian 13 from the netinst image: hostname `server`, domain `lan`, **no root password** (your user then gets `sudo`), the whole SSD as one partition, and only *SSH server* and *standard system utilities* selected.
3. If the installer says firmware files for `ath10k` are missing, answer **No**. They are optional calibration files that do not exist.
4. On lemonpi, add `<mac>,192.168.1.30,server` to `PIHOLE_DHCP_HOSTS` and recreate Pi-hole.
5. From your PC, copy your SSH key over: the bootstrap script turns password logins off once a key is there.
6. On the server (the repo is public; this is a full clone, because the Hub image is built from it):

```sh
sudo apt-get update && sudo apt-get install -y git
git clone https://github.com/Anwera64/homelab.git ~/homelab
cd ~/homelab
sudo hosts/server/bootstrap.sh --no-stack   # creates hosts/server/.env, sets the host up
sudo reboot                                 # when it asks; then run it again
```

7. Fill in `hosts/server/.env` (the comments in `.env.example` say where each value comes from), then start the stack:

```sh
sudo hosts/server/bootstrap.sh
```

The script is safe to rerun and asks for a reboot when one is pending. `--no-stack` does the host setup only, which is what you want until the settings and media have been moved over.

If the network is down after the install, the radios are probably in airplane mode: `echo 0 | sudo tee /sys/class/rfkill/rfkill*/soft`, then `sudo ifup wlp7s0`.

### Why nouveau is blocked first

The open-source `nouveau` driver hangs this GPU when it wakes it up. That freezes `lspci`, and with it the NVIDIA package install, until the machine is reset. So the script blocks `nouveau`, stops and asks for a reboot, and only installs the NVIDIA packages on the next run.

### Why the NVIDIA driver drives the console

The laptop stays open so it can ventilate, and `consoleblank=60` blanks the console after a minute. On the plain EFI console that only paints the panel black: its backlight stays lit. So the script also puts `nvidia-drm.modeset=1 nvidia-drm.fbdev=1` on the kernel command line. The NVIDIA driver then drives the console, and a blank console powers the panel off. To check, `cat /proc/fb` shows `nvidia-drmdrmfb` instead of `EFI VGA`.

## Updating

Every image in `docker-compose.yml` is pinned to a release, so the repo says what runs here. Renovate opens a bump PR once a release is three days old:

- **Minor and patch bumps** come in one PR, which merges itself once CI is green.
- **The Hub's Python packages** and its base image come in a PR of their own, merged once the backend's tests and image build pass. Renovate edits `requirements.in` and regenerates the lock files; on Mondays a weekly PR refreshes the packages nobody names (the indirect ones). A new Python version (3.12 to 3.13) waits for you.
- **Major bumps** get a PR each and wait for you. So does **Jellyfin**, whose upgrades migrate the library database. **Postgres** majors are never proposed: they need a dump and restore by hand.

`server-update.timer` applies what was merged at 04:00. It fast-forwards the checkout and, if anything changed, rebuilds and restarts the stack and removes the old images. When the restart fails (a registry dropping the pull, say), the next night tries again: the last commit the stack started from is kept as the ref `refs/server-update/applied`. It changes nothing when the checkout has local edits or commits of its own. To see what it did:

```sh
journalctl -u server-update
```

The timer only touches the stack. When a merge changes the host setup (`bootstrap.sh` or `system/`), it says so in that log, and you run the full update:

```sh
cd ~/homelab && git pull && sudo hosts/server/bootstrap.sh
```

To undo a bad bump, revert its PR on GitHub. The timer applies the revert the next night, or right away with `sudo systemctl start server-update`.

### Renovate runs on this machine

Renovate is the `renovate` service in `docker-compose.yml`. It is not part of the running stack: `server-renovate.timer` starts one run every hour, and the container exits when the run ends. A run opens the PRs that are due; later runs merge them once CI is green. The rules are in [`renovate.json`](../../renovate.json).

It needs a GitHub token, as `RENOVATE_TOKEN` in `.env`. Until that is set, every run is skipped. Create a fine-grained token for the `Anwera64/homelab` repository only, with these repository permissions, which are the ones Renovate's documentation lists:

- **Contents** (read and write): it pushes the bump branches and merges.
- **Pull requests** (read and write): it opens, updates and merges the PRs.
- **Issues** (read and write): it keeps a Dependency Dashboard issue listing what it found.
- **Commit statuses** (read and write): it posts a status while a release is younger than three days.
- **Workflows** (read and write): needed to push a branch that touches a workflow file.
- **Dependabot alerts** (read-only): it reads the repo's vulnerability alerts.

When the token expires the runs fail until it is replaced. The PRs are opened in the name of the token's owner.

```sh
sudo systemctl start server-renovate     # one run now
journalctl -u server-renovate            # what the runs did (also in Grafana, container renovate)
# What a run would do, without changing anything on GitHub:
docker compose --profile renovate run --rm -e RENOVATE_DRY_RUN=full renovate
```

## Firewall

Docker forwards a published port before the host's usual input rules see the packet, so there are two layers:

- **Service ports** are filtered in Docker's `DOCKER-USER` chain. Only the addresses in `SERVER_ALLOWED_SOURCES` (space-separated, in `.env`) get through. lemonpi is the default and the only one that needs it: everything else uses the `https://` names.
- **The host itself** drops incoming traffic except SSH from the LAN, ping and DHCP. This also covers IPv6.

One port can be opened for one address with `SERVER_PORT_SOURCES`, as `port=address` pairs. That is how the desktop's Alloy reaches Loki, which has no login of its own, and nothing else:

```ini
SERVER_PORT_SOURCES=3100=192.168.1.20
```

To change either list, edit `.env` and run `sudo systemctl restart server-firewall`.

To try a change to the rules themselves without risking your SSH session:

```sh
sudo hosts/server/system/server-firewall.sh --try       # applies; undoes itself after two minutes
sudo hosts/server/system/server-firewall.sh --confirm   # from a new SSH session, to keep it
```

`--clear` removes every rule.

## App logins

The firewall does not guard the `https://` names. lemonpi's Caddy forwards them, so every app sees its requests coming from lemonpi, a local address. An app set to skip the login for local addresses lets the whole LAN in. Each app therefore asks for its own login from every address.

Work through this once after the media services have moved here:

- [ ] Sonarr, Radarr, Prowlarr: Settings → General → Authentication Required = **Enabled** (not "Disabled for Local Addresses").
- [ ] Bazarr: Settings → General → Security → Authentication = **Form**. It ships with none.
- [ ] qBittorrent: Options → Web UI: untick "Bypass authentication for clients in whitelisted subnets". Untick the localhost box too, unless port forwarding is on (see "VPN provider" below). Then check that Sonarr, Radarr and Cleanuparr have the password saved in their download client, and put `QBITTORRENT_USERNAME` and `QBITTORRENT_PASSWORD` in lemonpi's `.env` for the dashboard.
- [ ] Cleanuparr: Settings → General: "Disable Auth for Local Addresses" **off**. Copy the API key from its account settings to `CLEANUPARR_API_KEY` in lemonpi's `.env` for the dashboard.
- [ ] Seerr, Jellystat: open each in a private window and confirm the login page comes first.
- [ ] Maintainerr has no login of its own. Caddy asks for a password on its name: `MAINTAINERR_USER` and `MAINTAINERR_PASSWORD_HASH` in lemonpi's `.env`. The password is on the name only, so the direct port (6246) must stay closed: the firewall here does that.
- [ ] FlareSolverr has no login and no page to use. It has no `https://` name and publishes no port: Prowlarr reaches it inside the stack at `http://gluetun:8191` (routed through the VPN). Confirm an indexer that uses it still tests green.

The dashboard's widgets keep working with the logins on: they use each app's API key, or the qBittorrent login above.

## VPN provider

qBittorrent reaches the internet only through Gluetun. The provider and its options are in `.env`:

| Variable | Default | What it does |
|---|---|---|
| `VPN_PROVIDER` | `nordvpn` | The provider, by its Gluetun name. |
| `WIREGUARD_PRIVATE_KEY` | none | That provider's WireGuard key. |
| `VPN_COUNTRY` | `United States` | The country of the VPN server. |
| `VPN_PORT_FORWARDING` | `off` | `on` asks the provider for a port other peers can reach qBittorrent on. |
| `VPN_PORT_FORWARD_ONLY` | `off` | `on` uses only the servers that forward ports. |
| `VPN_SERVER_HOSTNAMES` | empty | The servers to use within the country, by hostname, comma-separated (for example `node-nl-01.protonvpn.net,node-nl-02.protonvpn.net`). Empty means any server. |

NordVPN forwards no ports. ProtonVPN does on its paid plans: generate a WireGuard configuration on its site with "NAT-PMP (Port Forwarding)" ticked and copy its `PrivateKey`.

After a change, recreate the containers together, because qBittorrent and FlareSolverr have Gluetun's network:

```bash
cd ~/homelab/hosts/server && docker compose up -d --force-recreate gluetun flaresolverr qbittorrent
```

With port forwarding on, the port changes at every connection and Gluetun gives it to qBittorrent. For that, tick "Bypass authentication for clients on localhost" in qBittorrent (Options → Web UI): Gluetun is qBittorrent's localhost. Then confirm from another machine that the Web UI still asks for the password. When qBittorrent restarts alone it keeps the port it had: the compose file sets no listening port of its own.

Going back to a provider without port forwarding: untick that box again, in qBittorrent set Advanced → Network interface back to "Any" (the up command leaves it on the VPN's interface), and set the listening port back to 6881. Gluetun resets the port to 0 when forwarding stops; with a fixed port provider, set it back to 6881 so inbound connections reach qBittorrent directly.

### The VPN watchdog

Some ProtonVPN servers refuse the forwarded port or lose it every few minutes, and Gluetun does not retry after a refusal at its start. Gluetun cannot leave servers out, so the good ones are named in `VPN_SERVER_HOSTNAMES`. `server-vpn-watchdog.timer` runs `server-vpn-watchdog.sh` every minute. It does nothing unless `VPN_PORT_FORWARDING` is `on` in `.env`.

| It sees | It does |
|---|---|
| No forwarded port for ten minutes, or the port lost more than six times in an hour | Recreates gluetun, flaresolverr and qbittorrent together (qbittorrent only while the media disk is mounted). Gluetun then picks a server again. |
| After three recreations that brought no port | Lets Gluetun use any server in the country, ignoring `VPN_SERVER_HOSTNAMES`, picked from the same server list (see below: a stale list widens to stale servers). It goes back to the named servers at 04:00. |
| Six recreations in a row without a port | Tries only once an hour, because each recreate restarts qBittorrent. |
| A forwarded port, and qBittorrent listens on another port | Gives qBittorrent the forwarded port. Gluetun gives it only when forwarding comes up, so a qBittorrent that did not answer then would stay without it. |

Each recreate and each port given is one line in the journal: `journalctl -u server-vpn-watchdog.service`. The Downloads dashboard marks the recreations. To switch it off: `sudo systemctl disable --now server-vpn-watchdog.timer`.

### Refreshing the server list

Gluetun picks servers from a list built into its image, which ages: Proton renames servers, takes them out of P2P, or they go down, and Gluetun keeps connecting to their old addresses. Refresh the list when the named servers stop holding a port, or are missing from Proton's server page. The list is kept in `config/gluetun/servers.json`, so it survives recreates.

The pinned release cannot sign in to a Proton account with two-factor authentication; `qmcgaw/gluetun:latest` can. Run it once, outside the stack. It asks for the account's email, password and a fresh authenticator code (valid 30 seconds), and stores none of them:

```bash
mkdir -p ~/gluetun-refresh && \
read -rp "Proton email: " PE && IFS= read -rsp "Proton password: " PP && echo && \
read -rp "Authenticator code: " PT && \
docker run --rm -v ~/gluetun-refresh:/gluetun qmcgaw/gluetun:latest \
  update -providers protonvpn -proton-email "$PE" -proton-password "$PP" -proton-totp-code "$PT"; \
unset PE PP PT
```

"Incorrect login credentials" means the password, not the code. The latest image writes one file per provider; the pinned release reads only `servers.json`, which may hold just the Proton part (the other providers keep the built-in list). Copy it over and recreate:

```bash
cd ~/homelab && jq '{version: 1, protonvpn: .}' ~/gluetun-refresh/servers/protonvpn.json > config/gluetun/servers.json && \
cd hosts/server && docker compose up -d --force-recreate gluetun flaresolverr qbittorrent
```

Gluetun's log then says "Using protonvpn servers from file". Check that the hostnames in `VPN_SERVER_HOSTNAMES` are still in the new list with port forwarding: `jq -r '.servers[] | select(.vpn == "wireguard" and .port_forward and .country == "Netherlands") | "\(.server_name) \(.hostname)"' config/gluetun/servers.json`.

### HTTP proxy for indexers

Gluetun runs an internal HTTP proxy at `http://gluetun:8888` through the VPN tunnel. Services in the stack (such as Prowlarr) can use it to reach indexers that are blocked by ISP-level SNI resets or Cloudflare bans (e.g. 1337x):

1. In Prowlarr: **Settings → Indexers → Proxies**, click `+` and pick **HTTP**.
2. Set **Host** to `gluetun` and **Port** to `8888`.
3. Tag the proxy (e.g. `vpn`) and assign that tag to indexers like 1337x, or leave tags blank to route all indexers through the VPN.

## Media disk

All media lives on one disk mounted at `/data`, in the TRaSH layout, so downloads and the library share a filesystem and hardlinks work:

```
/data/media/movies
/data/media/tv
/data/media/anime
/data/media/anime-movies
/data/torrents
```

Anime has folders of its own because it has a quality profile of its own, `[Anime] Remux-1080p` from the TRaSH Guides, which Recyclarr keeps in Sonarr and Radarr beside the regular ones. In Sonarr an anime series also needs the series type "Anime". Seerr sends anime series there by itself (Settings → Services → Sonarr: the anime profile, folder and series type); for an anime movie, pick the profile and the folder in the request's advanced options. Jellyfin has a library for each of the two folders.

1. Format the disk ext4 and read its ID: `lsblk -o NAME,SIZE,FSTYPE,UUID`.
2. Put the ID in `DATA_DISK_UUID` in `.env` and rerun the bootstrap script. It adds the mount, creates the folders and starts the media services.

While the disk is not mounted, `/data` is an empty, read-only folder, so nothing can fill the SSD by mistake. If the disk drops out, the disk guard stops qBittorrent, the arr apps, Jellyfin and the other media services. The Hub, Seerr and the logs keep running.

Jellyfin runs as user 1000, like the other media apps, and sees the media read-only: it cannot change or delete a file on the disk. Whenever the stack starts, its settings folder is handed back to that user if anything in it belongs to someone else.

When the disk is plugged back in, the guard checks the filesystem, mounts it and starts the media services again, within about 15 seconds of the disk spinning up. If the check finds damage it cannot repair by itself, the guard leaves the disk unmounted and says so: run `sudo e2fsck -f` on the partition, then `sudo mount /data`. `journalctl -u server-media` shows what it did.

### Disk health

`smartd` runs a short self-test on the SSD and the media disk every Sunday at 03:00, and logs any change in their health counters. To read the results:

```sh
sudo smartctl -l selftest /dev/sda   # the media disk's test history
sudo smartctl -H -A /dev/sda         # health verdict and the counters (reallocated and pending sectors should be 0)
journalctl -u smartd                 # what smartd noticed
```

There is no scheduled extended test: it reads the whole surface and takes about 12 hours on this disk. Start one by hand with `sudo smartctl -t long /dev/sda` when in doubt.

## The server dashboard

It is in Grafana (https://grafana.spicy-llama.duckdns.org), named "Server", and opens on the last 7 days. Averages and maximums follow the period picked at the top right. It has six sections: Power, Temperatures, Load, Activity, Disks and Updates.

| Section | What it shows |
| --- | --- |
| Power | Uptime, mains on or off, battery charge. |
| Temperatures | CPU, GPU, media disk and SSD as gauges, with the average and maximum under each. The graph draws each as an average line inside a band from its lowest to its highest reading; the stretch of time each point covers follows the zoom and is never under a minute. Under it, how long the CPU was slowed down for heat. |
| Load | Average CPU, memory, GPU load and GPU memory use, and graphs over time. |
| Activity | Network traffic and disk reads and writes. |
| Disks | SMART verdict, reallocated and pending sectors, last self-test, SSD wear, whether the media disk is mounted, free space. |
| Updates | When the nightly update last ran and last applied something, when unattended upgrades last ran, whether a reboot is waiting. |

The gauge colours come from limits read from the hardware. The media disk turns amber at 55 °C and red at 60 °C. WD gives 65 °C as its maximum operating temperature.

The dashboard is the file `config/grafana/dashboards/server.json` in the repo. It can be changed and saved in Grafana. The next change to the file replaces it, so a change worth keeping goes back into the file. Grafana looks at the folder every 30 seconds, so a merged change shows up without a restart.

The host metrics timer and its folder are installed by the bootstrap script (`sudo hosts/server/bootstrap.sh`). Until it has run, the unattended upgrades and reboot tiles, the "mounted" tile and the self-test column stay empty; the nightly update's tiles fill in after its first run at 04:00. The self-test column also stays empty until the first Sunday self-test.

When a panel is empty, look at Prometheus' log and at its own target list:

```sh
docker logs prometheus
docker exec prometheus wget -qO- localhost:9090/api/v1/targets
```

## The downloads dashboard

Also in Grafana, named "Downloads", opening on the last 7 days. It shows how fast qBittorrent moves data and what around it changed. Speeds are in megabits a second.

| Part | What it shows |
| --- | --- |
| Average download, Peak download, Average upload | Three numbers for the period picked at the top right. The peak is the fastest two minutes. |
| Download, Upload | The speed over time, side by side. |
| Wi-Fi band | A strip: green on 5 GHz, orange on 2.4 GHz, where the card is several times slower. |
| Connectable | A strip: what qBittorrent says about being reachable, green or red ("firewalled"). It can stay green on a port nobody forwards. |
| Port forwarded | A strip: green while Gluetun holds a forwarded port, red while it has none. |
| Listening port | A strip: green while qBittorrent listens on the forwarded port, red while it listens on another, where no peer can connect to it. Empty while there is no forwarded port or qBittorrent does not answer. |
| Peer connections | How many peers qBittorrent is connected to. |
| Markers | Blue when the VPN connected (or changed server), red when port forwarding failed (every failure Gluetun logs), orange when the watchdog recreated the VPN. They can be switched off at the top. |

The speeds, the peers and "connectable" are qBittorrent's own numbers. `server-metrics.sh` reads them once a minute through Gluetun, with no login of its own: that works while "Bypass authentication for clients on localhost" is ticked in qBittorrent (see "VPN provider"). When qBittorrent does not answer, the script writes `server_qbittorrent_up 0` and those panels stay empty. "Port forwarded" is Gluetun's own answer (its port file), read once a minute by `server-metrics.sh`. A loss of the port shorter than a minute can fall between two readings of "Connectable"; the red markers are exact.

The Wi-Fi band comes from the node exporter's Wi-Fi collector.

The dashboard is the file `config/grafana/dashboards/downloads.json`, and follows the same rule as the server dashboard: a change worth keeping goes back into the file.

## Wi-Fi

The laptop is on Wi-Fi, and its card (one antenna, Wi-Fi 5) is several times faster on 5 GHz than on 2.4 GHz. Measured on 2026-10-08 with eight downloads at once:

| Band | Link rate | Download |
| --- | --- | --- |
| 2.4 GHz | 96 Mbit/s | 2 to 3 MB/s |
| 5 GHz | 433 Mbit/s | 17 to 20 MB/s |

The Livebox gives one network name on both bands. Once the card is on 2.4 GHz it stays there: `wpa_supplicant` does not leave a strong signal, and the 2.4 GHz one is strong.

`server-wifi-band.timer` checks every minute. When the card is on 2.4 GHz and the same network answers on 5 GHz at -70 dBm or better, it asks `wpa_supplicant` to roam there. It excludes no band and changes no settings, so 2.4 GHz is still the fallback when 5 GHz is out of reach. While the card is on 2.4 GHz each check scans, which can stall traffic for a few seconds.

```bash
iw dev wlp7s0 link                 # the band it is on now (freq) and the link rate
journalctl -u server-wifi-band     # one line for every move
```

If the journal shows a move every few minutes, the Livebox is sending the laptop back to 2.4 GHz. Switch the check off rather than let the two fight:

```bash
sudo systemctl disable --now server-wifi-band.timer
```

A cable to the Livebox makes all of this unnecessary: the check does nothing on a machine without a connected Wi-Fi card.

## Battery

The laptop's battery works as a small UPS. Conservation mode keeps it near 60% to slow its wear. On battery at 10% the watcher stops the containers and powers the laptop off. It does not power on by itself when the electricity returns: press the power button.

To rehearse the rule without shutting anything down:

```sh
sudo DRY_RUN=1 BATTERY_SHUTDOWN_PERCENT=100 hosts/server/system/server-battery.sh --once
```

## Checks

```sh
nvidia-smi                                             # the GTX 1660 Ti, driver 550
docker run --rm --gpus all debian:13-slim nvidia-smi   # the GPU inside a container
docker compose --project-directory ~/homelab/hosts/server ps
systemctl is-active server-firewall server-media server-battery
sudo iptables -S DOCKER-USER                           # the allowed addresses, then DROP
```

Jellyfin transcodes with NVENC (H.264 and HEVC). This GPU cannot encode AV1, so leave AV1 encoding off in Jellyfin's playback settings.

## Moving to other hardware

The setup is meant to outlive the laptop:

1. Install Debian on the new machine and give it the `192.168.1.30` reservation, so lemonpi and the Hub need no change.
2. Move the media disk over. Copy `config/` and the named volumes (`household_hub_data`, `grafana_data`, `loki_data`). `ollama_models` need not move: the bootstrap script pulls the model again.
3. In [`bootstrap.sh`](bootstrap.sh), the section between `# >>> laptop` and `# <<< laptop` is the hardware-specific part: the NVIDIA driver, the lid and the battery. Replace it for the new machine.
4. In [`docker-compose.yml`](docker-compose.yml), the `deploy` blocks of Jellyfin and Ollama are the only GPU settings. On an Intel machine Jellyfin's becomes a `/dev/dri` device for Quick Sync, and Ollama's goes: the embedder then runs on the CPU.

## The Hubs and their keys

Two Hubs run here, and they share neither data nor key:

| | Dev | Prod |
|---|---|---|
| Container | `household-hub-dev`, built from the checkout | `household-hub`, the released image `ghcr.io/anwera64/household-hub` |
| Changes | every merge, at 04:00 | when the `Hub prod` bump PR is merged, then at 04:00 |
| Data | volume `household_hub_data` | volume `household_hub_prod_data` |
| Key | `HUB_DEV_SECRET_KEY` | `HUB_SECRET_KEY` |
| Address | `https://hub-dev.spicy-llama.duckdns.org` | `https://hub.spicy-llama.duckdns.org` |
| From the server itself | `http://localhost:3051` | `http://localhost:3052` |

The household's apps (release builds) talk to prod; the dev app (debug builds) talks to dev.

Each key signs that Hub's login tokens and encrypts the calendar credentials stored in its database. A Hub refuses to start without its key, or on one shorter than 32 characters. Make each with `openssl rand -hex 32`, never copy the dev one's into prod, and never commit either.

Changing a key later has two costs:

- It signs every phone out; each member signs in again once.
- The stored calendar credentials can no longer be read. Re-encrypt them with the new key while the Hub is stopped, or reconnect each calendar in the app afterwards.

## Releasing the Hub

Prod only runs a released image:

1. A PR raises `__version__` in `apps/household-hub/backend/app/__init__.py`. The minor number is the MVP slice (`0.5.0`) and a fix within it is a patch (`0.5.1`).
2. Merging it is the release. [`release-tags.yml`](../../.github/workflows/release-tags.yml) tags `hub-v<version>`, and `hub-release.yml` runs the tests, pushes the private image and writes the release notes. It fails if the image can be pulled without signing in.
3. Renovate opens a `Hub prod` PR that moves the `household-hub` image to the new version, at its next hourly run. It never merges it by itself.
4. Merging that PR puts the version on prod at 04:00. To apply it at once: `sudo hosts/server/system/server-stack-up.sh`.

**Rolling back:** revert the `Hub prod` PR on GitHub, then run `server-stack-up.sh` or wait for 04:00. The database stays as the newer version left it, so a release whose migration changed it needs that migration undone first.

**Security fixes:** Renovate opens a PR, labelled `security`, when GitHub reports a vulnerable package in the hub's requirements. That PR also raises the patch version ([`tools/bump-hub-patch.sh`](../../tools/bump-hub-patch.sh)), so merging it releases the fix and the `Hub prod` PR follows. Routine package bumps reach dev only, and prod gets them with the next release. This needs Dependabot alerts on in the repository's settings.

**The private image** needs a **classic** GitHub token with `read:packages` only; GitHub Packages takes no fine-grained token. It is used twice:

- By the server's Docker, which pulls the image as root: `sudo docker login ghcr.io -u Anwera64`, with the token as the password. Docker keeps it in `/root/.docker/config.json`.
- By Renovate, to see new versions: `RENOVATE_GHCR_TOKEN` in `.env`.

When the token expires, prod keeps running the image it has, but a new version cannot be pulled and Renovate stops opening `Hub prod` PRs.

## Backups

`hosts/server/.env` is not in git. `config/` on the SSD holds every app's settings and databases; the Hubs' databases are in the `household_hub_prod_data` (prod) and `household_hub_data` (dev) volumes.
