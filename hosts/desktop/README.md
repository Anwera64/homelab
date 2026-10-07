# desktop: the chat model

The Windows PC with the RTX 5080, at the reserved address `192.168.1.20` (`desktop-kujo8mp.lan`). It is not always on. It runs Ollama for the chat model and an Alloy that ships its logs. Everything else runs on the server.

When the desktop is off or asleep, the Hub's chat does not answer. Everything else keeps working.

## What runs here

| Container | What it does |
| --- | --- |
| Ollama | The chat model, on the RTX 5080. Port 11434, open to the server only. Models live in the `ollama_models` Docker volume. |
| Alloy | Ships this machine's container logs to Loki on the server. |

## Setup

Prerequisites: Docker Desktop (WSL2 backend), the NVIDIA Container Toolkit, and Node.js 18+ for the tests.

```powershell
git clone git@github.com:Anwera64/homelab.git
cd homelab
git config core.hooksPath .githooks
Copy-Item hosts\desktop\.env.example hosts\desktop\.env
```

The settings in `hosts\desktop\.env`:

- `TZ`: the timezone.
- `CONFIG_PATH`: the shared `config/` folder in the repo root. Leave it at `../../config`.
- `OLLAMA_KEEP_ALIVE`: how long an idle model stays in video memory.
- `OLLAMA_KV_CACHE_TYPE`: the context cache precision.
- `LOKI_URL`: optional. Only set it if the server's address changes.

The scripts are run by name from any folder. For that, the folder `<repo>\hosts\desktop` must be on your user PATH. Add it once, with your own path:

```powershell
[Environment]::SetEnvironmentVariable('Path', [Environment]::GetEnvironmentVariable('Path', 'User') + ';C:\path\to\homelab\hosts\desktop', 'User')
```

Open a new terminal afterwards.

## Start and stop

- `startup_homelab.ps1` takes no flags. It checks Docker (and starts Docker Desktop if needed), creates `.env` from `.env.example` on the first run, runs `docker compose up -d` and reports containers that failed. Then it downloads, SHA256-checks and registers the models in `config/ollama-models/models.json` that Ollama is missing.
- `stop_homelab.ps1` runs `docker compose down`.

## Firewall

Set the network profile to **Private**. Ollama answers the server only. From an admin PowerShell:

```powershell
New-NetFirewallRule -DisplayName "Homelab Ollama (server)" -Direction Inbound -Protocol TCP -LocalPort 11434 -RemoteAddress 192.168.1.30 -Action Allow
```

The old rule that admitted the media ports from lemonpi is no longer needed. Remove it:

```powershell
Remove-NetFirewallRule -DisplayName "Homelab Stack (LAN)"
```

## Updating

Renovate pins the Ollama and Alloy versions and bumps them in the same weekly PR as the server's. After a bump is merged, run `git pull`, then `startup_homelab.ps1`. Docker fetches the newly pinned image.

## Disk space

- `compact_docker_disk.ps1` (admin) shrinks Docker's virtual disk. Run it after removing models or images.
- `enable_virtualization.ps1` is the one-time Hyper-V and WSL2 setup helper.

## Logs

The desktop's lines are in Grafana (Explore) at `https://grafana.spicy-llama.duckdns.org`. Search them with `{host="desktop", container="ollama"}`.
