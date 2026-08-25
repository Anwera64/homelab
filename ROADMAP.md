# 🚀 Homelab Media Stack Roadmap & Master Plan

This document serves as the persistent architectural roadmap for your self-hosted streaming and automation setup.

---

## 📌 Architecture Overview (Docker Compose Architecture)

| Component | Role | Details |
| :--- | :--- | :--- |
| **Caddy** | Reverse Proxy | Port 80/443 ingress, routing `homelab.local` & subdomains |
| **Gluetun** | VPN Gateway | NordVPN WireGuard (NordLynx) tunnel with automatic kill-switch |
| **qBittorrent** | Download Client | Routed strictly through Gluetun container network |
| **FlareSolverr** | Cloudflare Bypass | Standalone challenge bypass with direct DNS (1.1.1.1) to avoid VPN blacklisting |
| **Prowlarr** | Indexer Proxy | Public trackers (TorrentGalaxy, 1337x, BitSearch, Knaben, ShowRSS) |
| **Sonarr** | TV Automation | Series monitoring, custom Latin American audio formatting |
| **Radarr** | Movie Automation | Film monitoring, Extended/Special Edition scoring rules |
| **Bazarr** | Subtitles | Automated subtitle download and audio-offset sync |
| **Maintainerr** | Media Lifecycle | Automated cleanup rules for watched movies, TV seasons & disk management |
| **Jellyseerr** | Request & Discovery | Netflix-style search & request interface |
| **Jellystat** | Media Analytics | Event-based viewing analytics, stream history & user statistics |
| **Jellyfin** | Media Server | Hardware-accelerated (NVIDIA RTX 5080 NVENC/NVDEC) |
| **Homepage** | Unified Dashboard | Single-pane-of-glass status and telemetry |
| **Tailscale** | Remote Mesh VPN | Secure, encrypted zero-port-forwarding remote access |
| **Control Scripts** | Orchestration | [`startup_homelab.ps1`](./startup_homelab.ps1) & [`stop_homelab.ps1`](./stop_homelab.ps1) |

---

## 🗺️ Stack Relationship Diagram

```mermaid
graph TD
    subgraph Ingress [Ingress & Network Access]
        CLIENTS[📱 Home Wi-Fi Clients] -->|http://homelab.local| CADDY[🔒 Caddy Reverse Proxy :80]
        TAILSCALE[🔒 Tailscale Mesh VPN] -->|https://homelab.ts.net| CADDY
        CADDY --> HOMEPAGE[📊 Homepage Dashboard]
    end

    subgraph VPNNet [Gluetun VPN Network Isolation]
        GLUETUN[🛡️ Gluetun VPN - NordVPN WireGuard]
        QBIT[📥 qBittorrent]
    end

    subgraph ArrSuite [Arr Automation & Discovery]
        PROW[🔍 Prowlarr] --> RAD[🎬 Radarr] & SON[📺 Sonarr]
        FLARE[⚡ FlareSolverr - Direct DNS] --> PROW
        SEERR[✨ Jellyseerr] --> RAD & SON
        RAD & SON --> QBIT
        BAZ[📝 Bazarr] --> RAD & SON
        RECYC[♻️ Recyclarr - TRaSH Guides Auto-Sync] --> RAD & SON
    end

    subgraph MediaStorage [Local Media Libraries]
        QBIT -->|Downloads| DOWN["C:\Users\Anwera97\Downloads"]
        RAD -->|Moves/Links| MOVIES["C:\Users\Anwera97\Videos\Movies"]
        SON -->|Moves/Links| SHOWS["C:\Users\Anwera97\Videos\Shows"]
        MOVIES --> JELLY[🍿 Jellyfin Server - RTX 5080 NVENC]
        SHOWS --> JELLY
    end

    HOMEPAGE --> ArrSuite & JELLY & QBIT
```

---

## 🛠️ Management & Quick Reference

* **Start Entire Stack:**
  ```powershell
  .\startup_homelab.ps1
  # or: docker compose up -d
  ```

* **Stop Entire Stack:**
  ```powershell
  .\stop_homelab.ps1
  # or: docker compose down
  ```

* **Update All Services:**
  ```powershell
  docker compose pull && docker compose up -d
  ```

* **View Service Logs:**
  ```powershell
  docker compose logs -f <service-name>
  ```

---

## 🌐 Web UI Service Endpoints

| Service | Remote Ingress (DuckDNS HTTPS) | Local Ingress (Home Wi-Fi HTTP) | Role & Notes |
| :--- | :--- | :--- | :--- |
| **Homepage Portal** | [https://home.spicy-llama.duckdns.org](https://home.spicy-llama.duckdns.org) | [http://desktop-kujo8mp](http://desktop-kujo8mp) / `:80` | Central dashboard & unified status |
| **Jellyfin** | [https://jellyfin.spicy-llama.duckdns.org](https://jellyfin.spicy-llama.duckdns.org) | [http://desktop-kujo8mp:8096](http://desktop-kujo8mp:8096) | Media server (RTX 5080 NVENC Transcoding) |
| **Jellyseerr** | [https://seerr.spicy-llama.duckdns.org](https://seerr.spicy-llama.duckdns.org) | [http://desktop-kujo8mp:5055](http://desktop-kujo8mp:5055) | Media discovery & request manager |
| **Jellystat** | [https://stat.spicy-llama.duckdns.org](https://stat.spicy-llama.duckdns.org) | [http://desktop-kujo8mp:3005](http://desktop-kujo8mp:3005) | Playback analytics & viewer statistics |
| **qBittorrent** | [https://qbit.spicy-llama.duckdns.org](https://qbit.spicy-llama.duckdns.org) | [http://desktop-kujo8mp:8080](http://desktop-kujo8mp:8080) | Download client (Routed via Gluetun VPN) |
| **Sonarr** | [https://sonarr.spicy-llama.duckdns.org](https://sonarr.spicy-llama.duckdns.org) | [http://desktop-kujo8mp:8989](http://desktop-kujo8mp:8989) | TV series manager |
| **Radarr** | [https://radarr.spicy-llama.duckdns.org](https://radarr.spicy-llama.duckdns.org) | [http://desktop-kujo8mp:7878](http://desktop-kujo8mp:7878) | Movies manager |
| **Prowlarr** | [https://prowlarr.spicy-llama.duckdns.org](https://prowlarr.spicy-llama.duckdns.org) | [http://desktop-kujo8mp:9696](http://desktop-kujo8mp:9696) | Trackers & Indexers proxy |
| **Bazarr** | [https://bazarr.spicy-llama.duckdns.org](https://bazarr.spicy-llama.duckdns.org) | [http://desktop-kujo8mp:6767](http://desktop-kujo8mp:6767) | Subtitles sync |
| **Maintainerr** | [https://maintainerr.spicy-llama.duckdns.org](https://maintainerr.spicy-llama.duckdns.org) | [http://desktop-kujo8mp:6246](http://desktop-kujo8mp:6246) | Automated media lifecycle & cleanup |
| **Recyclarr** | Container (Cron `0 3 * * *`) | N/A | TRaSH Guides quality & format sync |
| **FlareSolverr** | [https://flaresolverr.spicy-llama.duckdns.org](https://flaresolverr.spicy-llama.duckdns.org) | [http://localhost:8191](http://localhost:8191) | Cloudflare bypass API |

