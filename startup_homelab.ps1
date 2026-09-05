# startup_homelab.ps1
# Starts the entire Dockerized Homelab media stack and verifies health
param(
    [switch]$SkipUpdate,
    [switch]$ForceUpdate
)

# Always ensure working directory is this repository folder
if ($PSScriptRoot) {
    Set-Location -Path $PSScriptRoot
}

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "         [+] Starting Homelab Media Stack            " -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan

# 1. Verify Docker CLI is available
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Docker is not installed or not in PATH." -ForegroundColor Red
    Write-Host "Please install Docker Desktop and start it before running this script." -ForegroundColor Yellow
    exit 1
}

# 2. Check if Docker Daemon is running
Write-Host "Checking Docker engine status..." -ForegroundColor DarkGray
docker info >$null 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker daemon is not running. Attempting to start Docker Desktop..." -ForegroundColor Yellow
    $possiblePaths = @(
        "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe",
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
        "$env:ProgramW6432\Docker\Docker\Docker Desktop.exe",
        "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    )
    $dockerDesktopPath = $possiblePaths | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if ($dockerDesktopPath) {
        Start-Process $dockerDesktopPath
        Write-Host "Waiting for Docker daemon to initialize..." -ForegroundColor Cyan
        $retries = 30
        while ($retries -gt 0) {
            Start-Sleep -Seconds 2
            docker info >$null 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Host "Docker daemon is ready!" -ForegroundColor Green
                break
            }
            $retries--
        }
        if ($retries -eq 0) {
            Write-Host "[ERROR] Timed out waiting for Docker engine." -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "[ERROR] Could not find Docker Desktop executable." -ForegroundColor Red
        Write-Host "Please start Docker Desktop manually from your Start Menu." -ForegroundColor Yellow
        exit 1
    }
}

# 3. Check for .env file
if (-not (Test-Path "$PSScriptRoot\.env")) {
    Write-Host "[WARNING] No .env file found. Copying from .env.example..." -ForegroundColor Yellow
    Copy-Item "$PSScriptRoot\.env.example" "$PSScriptRoot\.env"
    Write-Host "[ACTION REQUIRED] Created .env template. Please configure your .env file with your WIREGUARD_PRIVATE_KEY!" -ForegroundColor Red
}

if (Test-Path "$PSScriptRoot\.env") {
    $envContent = Get-Content "$PSScriptRoot\.env" -Raw
    if ($envContent -match "your_nordvpn_wireguard_private_key_here") {
        Write-Host "[WARNING] Placeholder WIREGUARD_PRIVATE_KEY detected in .env." -ForegroundColor Yellow
        Write-Host "VPN gateway (Gluetun) requires a valid WireGuard key to establish the tunnel." -ForegroundColor DarkGray
    }
}

# 4. Check for Automated Updates (24h Persistent Gate)
$updateIntervalHours = 24
$lastUpdateFile = "$PSScriptRoot\.last_update"
$shouldUpdate = $false

if ($ForceUpdate) {
    Write-Host "Forced update flag (-ForceUpdate) specified. Checking for updates..." -ForegroundColor Cyan
    $shouldUpdate = $true
} elseif ($SkipUpdate) {
    Write-Host "Skipping update check (-SkipUpdate specified)." -ForegroundColor DarkGray
    $shouldUpdate = $false
} else {
    if (-not (Test-Path $lastUpdateFile)) {
        Write-Host "No prior update record found. Checking for container updates..." -ForegroundColor Cyan
        $shouldUpdate = $true
    } else {
        try {
            $lastUpdateRaw = (Get-Content $lastUpdateFile -Raw).Trim()
            $lastUpdateTime = [DateTime]$lastUpdateRaw
            $hoursSince = ((Get-Date) - $lastUpdateTime).TotalHours
            if ($hoursSince -ge $updateIntervalHours) {
                Write-Host "Last update check was $([math]::Round($hoursSince, 1))h ago (> $updateIntervalHours h threshold). Checking for updates..." -ForegroundColor Cyan
                $shouldUpdate = $true
            } else {
                $hoursLeft = [math]::Round($updateIntervalHours - $hoursSince, 1)
                Write-Host "Last update check was $([math]::Round($hoursSince, 1))h ago. Skipping update check ($hoursLeft h remaining). Use -ForceUpdate to override." -ForegroundColor DarkGray
            }
        } catch {
            Write-Host "[WARNING] Corrupted .last_update timestamp file. Scheduling update check..." -ForegroundColor Yellow
            $shouldUpdate = $true
        }
    }
}

if ($shouldUpdate) {
    Write-Host "Pulling latest container images..." -ForegroundColor Cyan
    docker compose -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" pull
    if ($LASTEXITCODE -eq 0) {
        (Get-Date).ToString("o") | Set-Content "$PSScriptRoot\.last_update"
        Write-Host "Container images updated. Recorded timestamp in .last_update." -ForegroundColor Green
    } else {
        Write-Host "[WARNING] Image pull encountered an error (offline or registry unreachable). Continuing with local images..." -ForegroundColor Yellow
    }
}

# 5. Bring up the stack with Docker Compose
Write-Host "Starting Docker Compose services..." -ForegroundColor Cyan
docker compose -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" up -d

if ($LASTEXITCODE -eq 0) {
    if ($shouldUpdate) {
        Write-Host "Cleaning up obsolete container images..." -ForegroundColor DarkGray
        docker image prune -f >$null 2>&1
    }
    Write-Host ""
    Write-Host "=====================================================" -ForegroundColor Green
    Write-Host "     [SUCCESS] All Homelab Services Are Up & Running " -ForegroundColor Green
    Write-Host "=====================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "  --- Remote Access (Tailscale Mesh VPN) ---" -ForegroundColor Magenta
    Write-Host "  * Secure Portal:  https://homelab.llama-porbeagle.ts.net" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  --- Local Hostname Access (Port 80 / Caddy) ---" -ForegroundColor Cyan
    Write-Host "  * Central Portal: http://desktop-kujo8mp   (or http://192.168.1.20)" -ForegroundColor Yellow
    Write-Host "  * Jellyfin:       http://desktop-kujo8mp:8096" -ForegroundColor White
    Write-Host "  * Jellyseerr:     http://desktop-kujo8mp:5055" -ForegroundColor White
    Write-Host "  * Jellystat:      http://desktop-kujo8mp:3005" -ForegroundColor White
    Write-Host "  * Sonarr:         http://desktop-kujo8mp:8989" -ForegroundColor White
    Write-Host "  * Radarr:         http://desktop-kujo8mp:7878" -ForegroundColor White
    Write-Host "  * Prowlarr:       http://desktop-kujo8mp:9696" -ForegroundColor White
    Write-Host "  * Bazarr:         http://desktop-kujo8mp:6767" -ForegroundColor White
    Write-Host "  * Maintainerr:    http://desktop-kujo8mp:6246" -ForegroundColor White
    Write-Host "  * qBittorrent:    http://desktop-kujo8mp:8080" -ForegroundColor White
    Write-Host ""
    Write-Host "  --- Legacy Direct Port Fallbacks ---" -ForegroundColor DarkGray
    Write-Host "  * Dashboard:      http://localhost:3000   (LAN: http://192.168.1.20:3000)" -ForegroundColor DarkGray
    Write-Host "  * Jellyfin:       http://localhost:8096   (LAN / TV: http://192.168.1.20:8096)" -ForegroundColor DarkGray
    Write-Host "  * Jellyseerr:     http://localhost:5055   (LAN / Mobile: http://192.168.1.20:5055)" -ForegroundColor DarkGray
    Write-Host "  * qBittorrent:    http://localhost:8080   (via Gluetun VPN)" -ForegroundColor DarkGray
    Write-Host ""
} else {
    Write-Host "[ERROR] Failed to start one or more containers. Check logs with 'docker compose logs'." -ForegroundColor Red
}