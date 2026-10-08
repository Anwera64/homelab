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
| Metrics (Docker) | Prometheus keeps a year of the machine's own numbers. Three exporters report them: the node exporter (CPU, memory, disks, network, temperatures, battery), the SMART exporter (disk health and the media disk's temperature) and the GPU exporter (the GTX 1660 Ti, through nvidia-smi). None of the four has a login, so each publishes no port: only Grafana and Prometheus inside the stack reach them. |
| Host metrics (systemd timer) | Once every minute, `server-metrics.sh` writes down what no exporter reports: when unattended upgrades last ran, whether the media disk is mounted, whether a reboot is waiting, how long the CPU has been slowed down for heat, and each disk's latest self-test. The nightly update writes when it ran and when it last applied something. The node exporter reads both from `/var/lib/node_exporter/textfile`. |
| Firewall | The published service ports answer only the addresses in `SERVER_ALLOWED_SOURCES`. SSH answers the LAN. |
| Disk guard | Stops the media services while the media disk is unplugged. When it is plugged back in, checks the filesystem, mounts it and starts them again. |
| smartd | Runs a short self-test on both disks every Sunday at 03:00. |
| Battery watcher | Holds the charge near 60%. In a power cut it stops the containers and powers off at 10%. |
| Wi-Fi band (systemd timer) | Once every minute, moves the Wi-Fi back to 5 GHz when the card has settled on 2.4 GHz. |
| unattended-upgrades | Installs Debian security updates and Docker updates, and reboots at 05:00 when one needs it. |
| Renovate (Docker, systemd timer) | Runs every hour: opens the weekly version bump PRs on GitHub and merges the ones that may merge themselves once CI is green. |
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

## Updating

Every image in `docker-compose.yml` is pinned to a release, so the repo says what runs here. Renovate opens the bump PRs on Monday mornings:

- **Minor and patch bumps** come in one PR a week, which merges itself once CI is green.
- **The Hub's Python packages** and its base image come in a weekly PR of their own, merged once the backend's tests and image build pass. Renovate edits `requirements.in` and regenerates the lock files; a second weekly PR refreshes the packages nobody names (the indirect ones). A new Python version (3.12 to 3.13) waits for you.
- **Major bumps** get a PR each and wait for you. So does **Jellyfin**, whose upgrades migrate the library database. **Postgres** majors are never proposed: they need a dump and restore by hand.

`server-update.timer` applies what was merged at 04:00. It fast-forwards the checkout and, if anything changed, rebuilds and restarts the stack and removes the old images. It changes nothing when the checkout has local edits or commits of its own. To see what it did:

```sh
journalctl -u server-update
```

The timer only touches the stack. When a merge changes the host setup (`bootstrap.sh` or `system/`), it says so in that log, and you run the full update:

```sh
cd ~/homelab && git pull && sudo hosts/server/bootstrap.sh
```

To undo a bad bump, revert its PR on GitHub. The timer applies the revert the next night, or right away with `sudo systemctl start server-update`.

### Renovate runs on this machine

Renovate is the `renovate` service in `docker-compose.yml`. It is not part of the running stack: `server-renovate.timer` starts one run every hour, and the container exits when the run ends. A run on Monday morning opens the PRs; later runs merge them once CI is green. The rules are in [`renovate.json`](../../renovate.json).

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
- [ ] FlareSolverr has no login and no page to use. It has no `https://` name and publishes no port: Prowlarr reaches it inside the stack at `http://flaresolverr:8191`. Confirm an indexer that uses it still tests green.

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

NordVPN forwards no ports. ProtonVPN does on its paid plans: generate a WireGuard configuration on its site with "NAT-PMP (Port Forwarding)" ticked and copy its `PrivateKey`.

After a change, recreate both containers together, because qBittorrent has Gluetun's network:

```bash
cd ~/homelab/hosts/server && docker compose up -d --force-recreate gluetun qbittorrent
```

With port forwarding on, the port changes at every connection and Gluetun gives it to qBittorrent. For that, tick "Bypass authentication for clients on localhost" in qBittorrent (Options → Web UI): Gluetun is qBittorrent's localhost. Then confirm from another machine that the Web UI still asks for the password.

Going back to a provider without port forwarding: untick that box again, and in qBittorrent set the listening port back to 6881 and Advanced → Network interface back to "Any". Gluetun leaves them at port 0 and `lo` when forwarding stops, and nothing downloads until they are reset.

## Media disk

All media lives on one disk mounted at `/data`, in the TRaSH layout, so downloads and the library share a filesystem and hardlinks work:

```
/data/media/movies
/data/media/tv
/data/torrents
```

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

## The Hub's key

`HUB_SECRET_KEY` in `.env` signs every login token and encrypts the calendar credentials stored in the Hub's database. The Hub refuses to start without it, or on a key shorter than 32 characters. Make one with `openssl rand -hex 32` and never commit it.

Changing the key later has two costs:

- It signs every phone out; each member signs in again once.
- The stored calendar credentials can no longer be read. Re-encrypt them with the new key while the Hub is stopped, or reconnect each calendar in the app afterwards.

## Backups

`hosts/server/.env` is not in git. `config/` on the SSD holds every app's settings and databases; the Hub's database is in the `household_hub_data` volume.
