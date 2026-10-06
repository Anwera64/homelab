# server: the always-on app server

A Lenovo Legion Y540 (i7-9750HF, 16 GB, GTX 1660 Ti) on Debian 13 server, at the reserved address `192.168.1.30` (`server.lan`). It will run everything the desktop used to run except the chat model: the media server, the arr pipeline behind the VPN kill-switch, the Household Hub with its embedder, and the log stack.

## What is live

- **Live here:** the Household Hub, SearXNG, Loki, Grafana and Alloy. lemonpi's Caddy sends `hub.`, `grafana.` and `telemetry.` to this machine. An Ollama of its own runs the embedder (`bge-m3`) on the GTX 1660 Ti: it ranks what a chat turn reads. It unloads after 30 minutes without use, and if it is down the Hub ranks by keywords instead.
- **Still on the desktop:** the media services (Jellyfin, the arr apps, qBittorrent and their companions). They move once the media disk is in; until then the bootstrap script leaves them stopped here.
- **Staying on the desktop:** the chat model, in Ollama on the RTX 5080. The Hub reaches it over the LAN, so the chat model needs the desktop awake. The desktop also keeps an Alloy that ships its logs to this Loki.

## What runs here

| Piece | What it does |
| --- | --- |
| The stack (Docker) | The services in [`docker-compose.yml`](docker-compose.yml), each with a memory cap. The Hub asks the desktop's Ollama for the chat model and the stack's own Ollama for embeddings; the bootstrap script pulls `bge-m3` when it is missing. |
| Logs (Docker) | Loki keeps 30 days of every container's output, from this machine and the desktop. Grafana searches it at https://grafana.spicy-llama.duckdns.org. |
| Firewall | The published service ports answer only the addresses in `SERVER_ALLOWED_SOURCES`. SSH answers the LAN. |
| Disk guard | Stops the media services while the media disk is unplugged, and starts them again when it is back. |
| Battery watcher | Holds the charge near 60%. In a power cut it stops the containers and powers off at 10%. |
| unattended-upgrades | Installs Debian security updates and Docker updates, and reboots at 05:00 when one needs it. |
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

7. Fill in `hosts/server/.env` (same values as the desktop's `.env`), then start the stack:

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

## Media disk

All media lives on one disk mounted at `/data`, in the TRaSH layout, so downloads and the library share a filesystem and hardlinks work:

```
/data/media/movies
/data/media/tv
/data/torrents
```

1. Format the disk ext4 and read its ID: `lsblk -o NAME,SIZE,FSTYPE,UUID`.
2. Put the ID in `DATA_DISK_UUID` in `.env` and rerun the bootstrap script. It adds the mount, creates the folders and starts the media services.

While the disk is not mounted, `/data` is an empty, read-only folder, so nothing can fill the SSD by mistake. If the disk drops out, the disk guard stops qBittorrent, the arr apps, Jellyfin and the other media services, and starts them again when it is back. The Hub, Seerr and the logs keep running. `journalctl -u server-media` shows what it did.

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
