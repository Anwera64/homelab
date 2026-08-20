# 🛸 Automated Homelab Media & Streaming Stack

[![Docker](https://img.shields.io/badge/Docker-24.0+-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Docker Compose](https://img.shields.io/badge/Docker_Compose-v2-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![NVIDIA GPU](https://img.shields.io/badge/NVIDIA-RTX_5080_NVENC-76B900?logo=nvidia&logoColor=white)](https://www.nvidia.com/)
[![Tailscale](https://img.shields.io/badge/Tailscale-Mesh_VPN-2F5CFF?logo=tailscale&logoColor=white)](https://tailscale.com/)
[![Caddy](https://img.shields.io/badge/Caddy-Reverse_Proxy-1F88C0?logo=caddy&logoColor=white)](https://caddyserver.com/)
[![WireGuard](https://img.shields.io/badge/WireGuard-NordVPN_Killswitch-88171A?logo=wireguard&logoColor=white)](https://www.wireguard.com/)

A modern, fully automated, GPU-accelerated self-hosted media server and automation pipeline running on Docker Compose on Windows / WSL2. Features zero-copy atomic hardlinks, VPN kill-switch isolation, TRaSH Guides quality sync, and dual-mode local/remote ingress with official Let's Encrypt HTTPS via Tailscale.

---

## 🌟 Key Features

* **🍿 Hardware-Accelerated Streaming:** **Jellyfin** with full NVIDIA NVENC/NVDEC hardware transcoding for 4K HDR/Dolby Vision playback.
* **✨ Discovery & Requests:** **Jellyseerr** for seamless movie/TV discovery and one-click requests.
* **🤖 Complete *Arr Automation:** **Sonarr**, **Radarr**, **Prowlarr**, and **Bazarr** managing series, films, indexers, and automated subtitle synchronization.
* **♻️ TRaSH Guides Sync:** **Recyclarr** automatically syncs TRaSH quality profiles and custom formats (including Latin American Spanish audio scoring) daily.
* **🛡️ VPN Network Isolation:** **qBittorrent** and **FlareSolverr** are strictly routed through **Gluetun** (NordVPN WireGuard) with an automatic network kill-switch.
* **⚡ Zero-Copy Atomic Hardlinks:** Unified `/data` volume layout enables instantaneous, 0-byte hardlinks from download completion to media library without disk fragmentation.
* **🌐 Dual-Mode Ingress Routing:**
  * **Remote (Tailscale / 5G):** Official **Let's Encrypt HTTPS (Green Lock)** with zero port forwarding required.
  * **Local (Home Wi-Fi):** Native **Pure HTTP** on Port 80 / standard ports for zero-warning Smart TV and local PC access.
* **📊 Unified Dashboard:** **Homepage** single-pane-of-glass status portal with dynamic, client-side ingress link adaptation.
* **🧪 Quality Gate & Testing:** Built-in Node.js unit test suite and Git pre-commit hooks to guarantee zero broken deployments.

---

## 🗺️ Architecture & Topology

```mermaid
graph TD
    subgraph Ingress [Ingress & Access Layer]
        CLIENTS[📱 Home Wi-Fi Devices] -->|http://desktop-kujo8mp| CADDY[🔒 Caddy Reverse Proxy :80]
        TAILSCALE[🔒 Tailscale Mesh Gateway] -->|https://homelab.ts.net| CADDY
        CADDY --> HOMEPAGE[📊 Homepage Dashboard]
    end

    subgraph VPNNet [Gluetun VPN Network Isolation]
        GLUETUN[🛡️ Gluetun VPN - NordVPN WireGuard]
        QBIT[📥 qBittorrent Web UI]
        FLARE[⚡ FlareSolverr]
    end

    subgraph ArrSuite [Arr Automation & Quality Engine]
        PROW[🔍 Prowlarr Indexer Proxy] --> RAD[🎬 Radarr Movies] & SON[📺 Sonarr Series]
        SEERR[✨ Jellyseerr Request Portal] --> RAD & SON
        RAD & SON --> QBIT
        FLARE --> PROW
        BAZ[📝 Bazarr Subtitles] --> RAD & SON
        RECYC[♻️ Recyclarr - TRaSH Guides Sync] --> RAD & SON
    end

    subgraph MediaStorage [Unified Storage - Atomic Hardlinks]
        QBIT -->|1. Downloads| DOWN["/data/Downloads/complete"]
        RAD -->|2. Instant Hardlink| MOVIES["/data/Videos/Movies"]
        SON -->|2. Instant Hardlink| SHOWS["/data/Videos/Shows"]
        MOVIES --> JELLY[🍿 Jellyfin Server - RTX 5080 NVENC]
        SHOWS --> JELLY
    end

    HOMEPAGE --> ArrSuite & JELLY & QBIT
```

---

## 🌐 Web UI Service Endpoints

| Service | Remote Ingress (Tailscale HTTPS) | Local Ingress (Home Wi-Fi HTTP) | Role & Features |
| :--- | :--- | :--- | :--- |
| **Homepage Portal** | `https://homelab.<tailnet>.ts.net` | `http://desktop-kujo8mp` (Port 80) | Central dashboard & system telemetry |
| **Jellyfin Media** | `https://homelab.<tailnet>.ts.net:8443` | `http://desktop-kujo8mp:8096` | 4K NVENC Hardware Transcoding |
| **Jellyseerr** | `https://homelab.<tailnet>.ts.net:15055` | `http://desktop-kujo8mp:5055` | Netflix-style request & discovery portal |
| **qBittorrent** | `https://homelab.<tailnet>.ts.net:18080` | `http://desktop-kujo8mp:8080` | Download client (Kill-switch protected) |
| **Sonarr** | `https://homelab.<tailnet>.ts.net:18989` | `http://desktop-kujo8mp:8989` | TV Series management & monitoring |
| **Radarr** | `https://homelab.<tailnet>.ts.net:17878` | `http://desktop-kujo8mp:7878` | Movie collection management |
| **Prowlarr** | `https://homelab.<tailnet>.ts.net:19696` | `http://desktop-kujo8mp:9696` | Torrent indexer proxy & FlareSolverr |
| **Bazarr** | `https://homelab.<tailnet>.ts.net:16767` | `http://desktop-kujo8mp:6767` | Automated subtitle downloader & sync |
| **Recyclarr** | Container (Cron `0 3 * * *`) | N/A | TRaSH Guides quality profile sync |
| **FlareSolverr** | Container | `http://localhost:8191` | Cloudflare challenge bypass API |

---

## 📁 Repository Structure

```
.
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
│   └── homepage/
│       ├── adapt-links.js      # Dynamic client-side ingress link adapter
│       ├── custom.js           # Browser DOM link rewriter
│       └── services.yaml       # Dashboard service definitions & API widgets
└── tests/
    └── adapt-links.test.js     # Automated unit test suite (Node.js test runner)
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

* **Start Stack:** `.\startup_homelab.ps1`
* **Stop Stack:** `.\stop_homelab.ps1`
* **View Live Container Logs:**
  ```powershell
  docker compose logs -f <service-name>
  ```
* **Run Automated Unit Tests:**
  ```powershell
  node --test tests/adapt-links.test.js
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

This repository enforces strict code quality and configuration integrity. The Git pre-commit hook (`.githooks/pre-commit`) automatically executes on every commit:
1. Runs all Node.js unit tests in `tests/adapt-links.test.js`.
2. Validates `docker-compose.yml` syntax and environment variables.
3. Automatically aborts the commit if any test fails.

---

## 📄 License & Acknowledgments

* Media Management Suite: [LinuxServer.io](https://www.linuxserver.io/)
* Transcoding Engine: [Jellyfin](https://jellyfin.org/)
* Quality Guides: [TRaSH Guides](https://trash-guides.info/)
* Reverse Proxy: [Caddy](https://caddyserver.com/)
* Mesh Network: [Tailscale](https://tailscale.com/)
