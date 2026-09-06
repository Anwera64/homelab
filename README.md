# 🛸 Automated Homelab Media & Streaming Stack

[![CI](https://github.com/Anwera64/homelab/actions/workflows/test.yml/badge.svg)](https://github.com/Anwera64/homelab/actions/workflows/test.yml)
[![Docker](https://img.shields.io/badge/Docker-24.0+-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Docker Compose](https://img.shields.io/badge/Docker_Compose-v2-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![NVIDIA GPU](https://img.shields.io/badge/NVIDIA-RTX_5080_NVENC_%26_Compute-76B900?logo=nvidia&logoColor=white)](https://www.nvidia.com/)
[![Tailscale](https://img.shields.io/badge/Tailscale-Mesh_VPN-2F5CFF?logo=tailscale&logoColor=white)](https://tailscale.com/)
[![Caddy](https://img.shields.io/badge/Caddy-Reverse_Proxy-1F88C0?logo=caddy&logoColor=white)](https://caddyserver.com/)
[![WireGuard](https://img.shields.io/badge/WireGuard-NordVPN_Killswitch-88171A?logo=wireguard&logoColor=white)](https://www.wireguard.com/)
[![Ollama](https://img.shields.io/badge/Ollama-Local_LLMs-black?logo=ollama&logoColor=white)](https://ollama.ai/)
[![Open WebUI](https://img.shields.io/badge/Open_WebUI-AI_Workspace-blue?logo=openai&logoColor=white)](https://openwebui.com/)
[![Perplexica](https://img.shields.io/badge/Perplexica-AI_Research_Engine-20B8CD?logo=searxng&logoColor=white)](https://github.com/ItzCrazyKns/Vane)
[![SearXNG](https://img.shields.io/badge/SearXNG-Privacy_Search-3C4043?logo=google&logoColor=white)](https://searxng.org/)

A modern, fully automated, GPU-accelerated self-hosted media server, automation pipeline, and private AI intelligence & research stack running on Docker Compose on Windows / WSL2. Features zero-copy atomic hardlinks, VPN kill-switch isolation, TRaSH Guides quality sync, dual-mode local/remote ingress with official Let's Encrypt HTTPS via Tailscale, and a local privacy-first AI intelligence engine powered by Ollama (RTX 5080), Open WebUI, Perplexica/Vane, and SearXNG.

---

## 🌟 Key Features

* **🧠 Local AI & Autonomous Research Engine:**
  * **Ollama (RTX 5080 GPU):** Hardware-accelerated local inference and embeddings hosting `qwen2.5:14b`, `mistral-small:22b-instruct-2409-q3_K_M`, `deepseek-r1:14b`, and `nomic-embed-text`.
  * **Open WebUI:** Modern private AI workspace with persistent conversation memory, SearXNG-powered live web search RAG, and an automated "Loading model into memory..." status indicator filter.
  * **Perplexica / Vane:** Privacy-first autonomous AI answering engine (Perplexity AI alternative) performing multi-round web research and source citation synthesis using local LLMs and embeddings.
  * **SearXNG:** Self-hosted private search aggregator powering both Perplexica and Open WebUI with real-time JSON search results (Brave, Wikipedia, Wikidata, Arxiv, Bing, WolframAlpha) without tracking or rate limits.
* **🍿 Hardware-Accelerated Streaming:** **Jellyfin** with full NVIDIA NVENC/NVDEC hardware transcoding for 4K HDR/Dolby Vision playback.
* **✨ Discovery & Requests:** **Jellyseerr** for seamless movie/TV discovery and one-click requests.
* **🤖 Complete *Arr Automation:** **Sonarr**, **Radarr**, **Prowlarr**, and **Bazarr** managing series, films, indexers, and automated subtitle synchronization.
* **♻️ TRaSH Guides Sync:** **Recyclarr** automatically syncs TRaSH quality profiles and custom formats (including Latin American Spanish audio scoring) daily.
* **🛡️ VPN Network Isolation & Modern UI:** **qBittorrent** with **VueTorrent** WebUI skin, strictly isolated and routed through **Gluetun** (NordVPN WireGuard) with an automatic network kill-switch. FlareSolverr uses direct DNS resolvers to ensure reliable Cloudflare challenge bypass.
* **⚡ Zero-Copy Atomic Hardlinks:** Unified `/data` volume layout enables instantaneous, 0-byte hardlinks from download completion to media library without disk fragmentation.
* **🌐 Dual-Mode Ingress Routing:**
  * **Remote (Tailscale / 5G):** Official **Let's Encrypt HTTPS (Green Lock)** with zero port forwarding required.
  * **Local (Home Wi-Fi):** Native **Pure HTTP** on Port 80 / standard ports for zero-warning Smart TV and local PC access.
* **📊 Unified Dashboard:** **Homepage** single-pane-of-glass status portal with dynamic, client-side ingress link adaptation.
* **🔄 Automated Updates & Lifecycle:** Intelligent 24h persistent timestamp boot checks in PowerShell combined with **Watchtower** (daily 4 AM cron) and automated Docker image layer pruning.
* **🧪 Quality Gate & Testing:** Built-in Node.js unit test suite and Git pre-commit hooks to guarantee zero broken deployments.

---

## 🗺️ Architecture & Topology

```mermaid
graph TD
    subgraph Ingress [Ingress & Access Layer]
        CLIENTS[📱 Home Wi-Fi Devices] -->|http://desktop-kujo8mp| CADDY[🔒 Caddy Reverse Proxy :80]
        TAILSCALE[🔒 Tailscale Mesh Gateway] -->|https://homelab.ts.net| CADDY
        CADDY --> HOMEPAGE[📊 Homepage Dashboard]
        CADDY -->|ai.spicy-llama.duckdns.org| WEBUI[🤖 Open WebUI :8080]
        CADDY -->|research.spicy-llama.duckdns.org| PERPLEXICA[🧭 Perplexica / Vane :3000]
    end

    subgraph AIStack [Local AI & Autonomous Research Engine]
        OLLAMA[🦙 Ollama Server - RTX 5080 GPU]
        SEARX[🔍 SearXNG Search Aggregator :8080]
        WEBUI -->|LLM Inference & RAG Embeddings| OLLAMA
        WEBUI -->|RAG Web Search JSON| SEARX
        PERPLEXICA -->|Agentic Search Tools| SEARX
        PERPLEXICA -->|Tool Calling & Embeddings| OLLAMA
    end

    subgraph VPNNet [Gluetun VPN Network Isolation]
        GLUETUN[🛡️ Gluetun VPN - NordVPN WireGuard]
        QBIT[📥 qBittorrent Web UI]
    end

    subgraph ArrSuite [Arr Automation & Quality Engine]
        PROW[🔍 Prowlarr Indexer Proxy] --> RAD[🎬 Radarr Movies] & SON[📺 Sonarr Series]
        FLARE[⚡ FlareSolverr Challenge Solver] --> PROW
        SEERR[✨ Jellyseerr Request Portal] --> RAD & SON
        RAD & SON --> QBIT
        BAZ[📝 Bazarr Subtitles] --> RAD & SON
        RECYC[♻️ Recyclarr - TRaSH Guides Sync] --> RAD & SON
        MAINT[🧹 Maintainerr - Automated Cleanup] --> RAD & SON
    end

    subgraph MediaStorage [Unified Storage - Atomic Hardlinks]
        QBIT -->|1. Downloads| DOWN["/data/Downloads/complete"]
        RAD -->|2. Instant Hardlink| MOVIES["/data/Videos/Movies"]
        SON -->|2. Instant Hardlink| SHOWS["/data/Videos/Shows"]
        MOVIES --> JELLY[🍿 Jellyfin Server - RTX 5080 NVENC]
        SHOWS --> JELLY
        JELLY --> JSTAT[📊 Jellystat - Playback Analytics]
        JELLY --> MAINT
    end

    HOMEPAGE --> ArrSuite & JELLY & QBIT & JSTAT & WEBUI & PERPLEXICA
```

---

## 🌐 Web UI Service Endpoints

| Service | Remote Ingress (DuckDNS HTTPS) | Local Ingress (Home Wi-Fi HTTP) | Role & Features |
| :--- | :--- | :--- | :--- |
| **Homepage Portal** | `https://home.spicy-llama.duckdns.org` | `http://desktop-kujo8mp` (Port 80) | Central dashboard & system telemetry |
| **Jellyfin Media** | `https://jellyfin.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:8096` | 4K NVENC Hardware Transcoding |
| **Jellyseerr** | `https://seerr.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:5055` | Netflix-style request & discovery portal |
| **Jellystat** | `https://stat.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:3005` | Playback analytics & viewer statistics |
| **qBittorrent** | `https://qbit.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:8080` | Download client (VueTorrent UI, Kill-switch protected) |
| **Sonarr** | `https://sonarr.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:8989` | TV Series management & monitoring |
| **Radarr** | `https://radarr.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:7878` | Movie collection management |
| **Prowlarr** | `https://prowlarr.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:9696` | Torrent indexer proxy & FlareSolverr |
| **Bazarr** | `https://bazarr.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:6767` | Automated subtitle downloader & sync |
| **Maintainerr** | `https://maintainerr.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:6246` | Automated media lifecycle & cleanup |
| **Recyclarr** | Container (Cron `0 3 * * *`) | N/A | TRaSH Guides quality profile sync |
| **Watchtower** | Container (Cron `0 0 4 * * *`) | N/A | Automated image updates & stale image pruning |
| **FlareSolverr** | `https://flaresolverr.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:8191` | Cloudflare challenge bypass API |
| **Open WebUI (AI)** | `https://ai.spicy-llama.duckdns.org` | `http://desktop-kujo8mp:3080` | Local LLM workspace (RTX 5080 GPU, Memory Loading Filter, SearXNG RAG) |
| **Perplexica (Vane)** | `https://research.spicy-llama.duckdns.org` *(Tailscale/LAN)* | `http://desktop-kujo8mp:3001` | Autonomous AI research & cited answer engine (SearXNG powered) |
| **SearXNG Aggregator** | Internal Container (`http://searxng:8080`) | N/A | Private search backend (JSON API, Wolfram Alpha enabled) |

---

## 📁 Repository Structure

```
.
├── .github/
│   └── workflows/
│       └── test.yml            # Automated CI workflow (Node.js test suites & Compose validation)
├── docker-compose.yml          # Master multi-container service orchestration
├── .env.example                # Environment variables template
├── startup_homelab.ps1         # One-click healthcheck & startup script
├── stop_homelab.ps1            # Clean shutdown script
├── enable_virtualization.ps1   # Windows Hyper-V / WSL2 setup helper
├── AGENTS.md                   # Strict development & pair-programming rules
├── ROADMAP.md                  # Master architecture & enhancement roadmap
├── .githooks/
│   └── pre-commit              # Automated pre-commit test & syntax quality gate
├── config/
│   ├── caddy/
│   │   └── Caddyfile           # Reverse proxy routing & Tailscale TLS configuration
│   ├── searxng/
│   │   └── settings.yml        # SearXNG aggregator engine & JSON API configuration
│   ├── open-webui/
│   │   ├── filters/
│   │   │   └── model_loading_indicator.py # Model memory loading status indicator filter
│   │   └── register_filter.py  # Automation script to register filter in Open WebUI
│   └── homepage/
│       ├── adapt-links.js      # Dynamic client-side ingress link adapter
│       ├── custom.js           # Browser DOM link rewriter
│       ├── custom.css          # Homepage dashboard styling overrides
│       ├── services.yaml       # Dashboard service definitions & API widgets
│       ├── settings.yaml       # Homepage global settings & layout
│       ├── widgets.yaml        # Dashboard system & search widgets
│       ├── bookmarks.yaml      # Quick bookmark links
│       └── docker.yaml         # Local Docker daemon socket provider
└── tests/
    ├── adapt-links.test.js        # Link adapter unit test suite
    ├── config-integrity.test.js   # Cross-configuration & port alignment test suite
    └── scripts-validation.test.js # PowerShell automation scripts test suite
```

---

## 🚀 Quickstart Guide

### 1. Prerequisites
* **Docker Desktop for Windows** (with WSL2 Backend enabled).
* **NVIDIA Container Toolkit** (for GPU transcoding support).
* **Node.js v18+** (for running local validation unit tests).

### 2. Setup Environment
Clone the repository and copy the environment template:
```powershell
git clone git@github.com:Anwera64/homelab.git
cd homelab
Copy-Item .env.example .env
```

Edit `.env` to configure your storage paths and VPN credentials:
```ini
# Storage Paths
MEDIA_ROOT=C:/Users/<Username>
MOVIES_PATH=C:/Users/<Username>/Videos/Movies
SHOWS_PATH=C:/Users/<Username>/Videos/Shows
CONFIG_PATH=C:/Users/<Username>/Documents/Repos/Homelab/config

# VPN Configuration (Gluetun - NordVPN)
WIREGUARD_PRIVATE_KEY=your_nordvpn_wireguard_private_key
VPN_COUNTRY=United States

# Tailscale Remote Access
TS_AUTHKEY=tskey-auth-your-tailscale-key
TS_HOSTNAME=homelab
```

### 3. Enable Git Pre-Commit Hook
```powershell
git config core.hooksPath .githooks
```

### 4. Start the Homelab Stack
Run the automated startup script:
```powershell
.\startup_homelab.ps1
```

---

## 🛠️ Management & Operations

* **Start Stack (with 24h Update Check & Auto-Prune):** `.\startup_homelab.ps1`
  * Fully initializes both the Media automation pipeline and the AI stack (`profiles: ["ai"]`).
  * Automatically verifies and pulls required Ollama models (`qwen2.5:14b`, `mistral-small:22b-instruct-2409-q3_K_M`, `deepseek-r1:14b`, `nomic-embed-text`).
  * Automatically registers and activates the Open WebUI memory loading indicator filter.
* **Start Media Stack Only (Without AI Services):** `.\startup_homelab.ps1 -DisableAI`
* **Fast Start (Bypass Update Check for Instant Boot):** `.\startup_homelab.ps1 -SkipUpdate`
* **Force Update on Boot:** `.\startup_homelab.ps1 -ForceUpdate`
* **Stop Stack:** `.\stop_homelab.ps1`
* **Watchtower Manual Update Check:**
  ```powershell
  docker compose run --rm watchtower --run-once
  ```
* **View Live Container Logs:**
  ```powershell
  docker compose logs -f <service-name>
  ```
* **Run Automated Unit Tests:**
  ```powershell
  node --test
  ```

---

## 🍿 Dynamic Bitrate & Remote Streaming Guide

Jellyfin is configured with full **NVIDIA NVENC/NVDEC hardware acceleration** (RTX 5080) with real-time HEVC/AV1 encoding and HDR tonemapping to guarantee smooth playback across any network connection:

* **🏠 Home Wi-Fi / Local LAN:** Automatically DirectPlays full uncompressed 4K HDR Remux streams (80–100+ Mbps) with zero transcoding overhead.
* **🌐 Remote Networks (5G / Hotel / External Wi-Fi):**
  * The server enforces a **40 Mbps remote ceiling**, preventing raw 4K Remuxes from saturating cellular or remote bandwidth.
  * When client quality is set to **"Auto"**, the player dynamically tests connection speed and requests the optimal transcode tier (e.g. 4 Mbps on slow Wi-Fi, 15 Mbps on medium, 35 Mbps on 5G/fast Wi-Fi).
  * The RTX 5080 GPU transcodes streams on-the-fly at **>10x real-time speed** (<0.05s per segment), eliminating buffering.

### Recommended Client App Settings (Jellyfin Mobile / Android TV / Web)
1. **Bitrate / Playback Quality:** Set to **Auto** (Settings ➔ Playback ➔ Bitrate).
2. **Video Player Type:** Set to **ExoPlayer** (Android / Google TV) for hardware-accelerated HLS playback.
3. **In-Video Quality Selector:** Can be toggled on-the-fly (e.g., down to 1080p 10 Mbps or 720p 4 Mbps) if traveling through low-signal areas.

---

## 🧪 Quality Gate & Automated Testing

This repository enforces strict code quality and configuration integrity across local and continuous integration environments:

### 1. Automated Test Suites (`tests/`)
Running `node --test` executes 35+ automated validation checks across three dedicated suites:
* **Ingress Link Adaptation (`adapt-links.test.js`):** Verifies that Homepage dashboard links adapt dynamically between secure Tailscale HTTPS ports and pure local HTTP LAN ports.
* **Cross-Config Integrity (`config-integrity.test.js`):** Validates that all ports, services, and tokens across `Caddyfile`, `docker-compose.yml`, `services.yaml`, and `.env.example` stay 100% synchronized.
* **PowerShell Automation (`scripts-validation.test.js`):** Validates startup, shutdown, and Hyper-V/WSL2 setup scripts.

### 2. Git Pre-Commit Hook (`.githooks/pre-commit`)
Automatically executed prior to every local commit:
1. Runs the full Node.js test suite (`node --test`).
2. Validates `docker-compose.yml` syntax and variable bindings (`docker compose config -q`).
3. Automatically aborts the commit if any validation fails.

### 3. Continuous Integration (`.github/workflows/test.yml`)
GitHub Actions executes the full test runner and Docker Compose validation across all pushes and pull requests to `main` and `master`.

---

## 📄 License & Acknowledgments

* Media Management Suite: [LinuxServer.io](https://www.linuxserver.io/)
* Torrent WebUI Skin: [VueTorrent](https://github.com/VueTorrent/VueTorrent)
* Transcoding Engine: [Jellyfin](https://jellyfin.org/)
* Quality Guides: [TRaSH Guides](https://trash-guides.info/)
* Reverse Proxy: [Caddy](https://caddyserver.com/)
* Mesh Network: [Tailscale](https://tailscale.com/)
* Local AI Inference Engine: [Ollama](https://ollama.ai/)
* Private AI Workspace: [Open WebUI](https://openwebui.com/)
* Autonomous Research Engine: [Perplexica / Vane](https://github.com/ItzCrazyKns/Vane)
* Privacy Search Aggregator: [SearXNG](https://searxng.org/)
