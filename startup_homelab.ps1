# startup_homelab.ps1
# Starts the entire Dockerized Homelab media stack and verifies health
param(
    [switch]$SkipUpdate,
    [switch]$ForceUpdate,
    [switch]$NoAI,
    [switch]$ArrOnly
)

# Always ensure working directory is this repository folder
if ($PSScriptRoot) {
    Set-Location -Path $PSScriptRoot
}

# Handle selective profile switches (-NoAI / -ArrOnly)
$aiEnabled = $true
$profileArg = @()
if ($NoAI -or $ArrOnly) {
    Write-Host "Bypassing AI module (-NoAI / -ArrOnly specified). Starting media stack only..." -ForegroundColor DarkGray
    $profileArg = @("--profile", "none")
    $aiEnabled = $false
    # Ensure any running AI containers are stopped when explicitly opting out
    docker compose -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" --profile ai stop >$null 2>&1
} else {
    if (Test-Path "$PSScriptRoot\.env") {
        $envLines = Get-Content "$PSScriptRoot\.env"
        foreach ($line in $envLines) {
            if ($line -match '^\s*COMPOSE_PROFILES\s*=\s*(.*)$') {
                $val = $matches[1].Trim()
                if ($val -notmatch "ai") {
                    $aiEnabled = $false
                }
            }
        }
    }
}

if ($aiEnabled) {
    if (-not (Test-Path "$PSScriptRoot\config\open-webui")) {
        New-Item -ItemType Directory -Path "$PSScriptRoot\config\open-webui" -Force >$null
    }
    if (-not (Test-Path "$PSScriptRoot\config\ollama")) {
        New-Item -ItemType Directory -Path "$PSScriptRoot\config\ollama" -Force >$null
    }
    if (-not (Test-Path "$PSScriptRoot\config\searxng")) {
        New-Item -ItemType Directory -Path "$PSScriptRoot\config\searxng" -Force >$null
    }
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
    docker compose @profileArg -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" pull
    if ($LASTEXITCODE -eq 0) {
        (Get-Date).ToString("o") | Set-Content "$PSScriptRoot\.last_update"
        Write-Host "Container images updated. Recorded timestamp in .last_update." -ForegroundColor Green
    } else {
        Write-Host "[WARNING] Image pull encountered an error (offline or registry unreachable). Continuing with local images..." -ForegroundColor Yellow
    }
}

# 5. Staged Launch Sequence (Gluetun -> qBittorrent -> Full Media Stack)
Write-Host "[1/3] Starting Gluetun VPN Gateway..." -ForegroundColor Cyan
docker compose -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" up -d gluetun
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Failed to start Gluetun VPN container." -ForegroundColor Red
    Write-Host "  -> Check logs with: docker logs gluetun" -ForegroundColor Yellow
}

Write-Host "Waiting for Gluetun VPN tunnel to become healthy..." -ForegroundColor DarkGray
$gluetunTimeout = 45
$gluetunHealthy = $false
while ($gluetunTimeout -gt 0) {
    $health = (docker inspect gluetun --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}starting{{end}}" 2>$null)
    if ($health -eq "healthy") {
        $gluetunHealthy = $true
        Write-Host "Gluetun VPN is healthy and tunnel is active!" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 2
    $gluetunTimeout -= 2
}

if (-not $gluetunHealthy) {
    Write-Host "[ERROR] Gluetun failed to reach healthy status within 45s. VPN connection or WireGuard key may need attention." -ForegroundColor Red
    Write-Host "  -> Check logs with: docker logs gluetun" -ForegroundColor Yellow
}

Write-Host "[2/3] Starting qBittorrent client..." -ForegroundColor Cyan
docker compose -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" up -d qbittorrent
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Failed to start qBittorrent container." -ForegroundColor Red
    Write-Host "  -> Check logs with: docker logs qbittorrent" -ForegroundColor Yellow
}

Write-Host "[3/3] Starting remaining Homelab services..." -ForegroundColor Cyan
docker compose @profileArg -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Docker compose encountered an error during service startup." -ForegroundColor Red
    Write-Host "  -> Check logs with: docker compose logs" -ForegroundColor Yellow
}

# 6. Post-Startup Container Health Audit
Write-Host "Verifying container health statuses..." -ForegroundColor DarkGray
Start-Sleep -Seconds 2

$failedContainers = @()
$containerIds = docker compose @profileArg -f "$PSScriptRoot\docker-compose.yml" ps -a -q 2>$null
if ($containerIds) {
    foreach ($cId in $containerIds) {
        $cName = (docker inspect $cId --format "{{.Name}}").TrimStart('/')
        $status = docker inspect $cId --format "{{.State.Status}}"
        $restarting = docker inspect $cId --format "{{.State.Restarting}}"
        $exitCode = docker inspect $cId --format "{{.State.ExitCode}}"
        $health = docker inspect $cId --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}"

        if (-not $aiEnabled -and ($cName -eq "ollama" -or $cName -eq "open-webui")) {
            continue
        }

        if ($restarting -eq "true" -or $status -eq "exited" -or $health -eq "unhealthy") {
            $failedContainers += [PSCustomObject]@{
                Name     = $cName
                Status   = $status
                Health   = $health
                ExitCode = $exitCode
            }
        }
    }
}

if ($failedContainers.Count -gt 0) {
    Write-Host ""
    Write-Host "=====================================================" -ForegroundColor Red
    Write-Host "  [ERROR] The following container(s) failed to load: " -ForegroundColor Red
    Write-Host "=====================================================" -ForegroundColor Red
    foreach ($failed in $failedContainers) {
        Write-Host "  * $($failed.Name) - Status: $($failed.Status), Health: $($failed.Health), ExitCode: $($failed.ExitCode)" -ForegroundColor Red
        Write-Host "    -> Inspect logs with: docker logs $($failed.Name)" -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "[ACTION REQUIRED] Check the logs for failing containers above." -ForegroundColor Yellow
} else {
    if ($shouldUpdate) {
        Write-Host "Cleaning up obsolete container images..." -ForegroundColor DarkGray
        docker image prune -f >$null 2>&1
    }

    if ($aiEnabled) {
        $ollamaRunning = (docker inspect ollama --format "{{.State.Status}}" 2>$null) -eq "running"
        if ($ollamaRunning) {
            Write-Host "Checking local AI starter models in Ollama..." -ForegroundColor Cyan
            $requiredModels = @("qwen3:14b", "deepseek-v4-flash", "bge-m3")
            $installedModelsRaw = (docker exec ollama ollama list 2>$null) -join "`n"
            foreach ($model in $requiredModels) {
                if ($installedModelsRaw -notmatch [regex]::Escape($model)) {
                    Write-Host "  [+] Pulling missing starter model '$model' into config/ollama..." -ForegroundColor Yellow
                    docker exec ollama ollama pull $model
                    if ($LASTEXITCODE -eq 0) {
                        Write-Host "  [SUCCESS] Model '$model' is ready!" -ForegroundColor Green
                    } else {
                        Write-Host "  [WARNING] Failed to pull model '$model'. You can pull it later via Open WebUI or 'docker exec ollama ollama pull $model'." -ForegroundColor Yellow
                    }
                } else {
                    Write-Host "  * Model '$model' is ready." -ForegroundColor Green
                }
            }
        }

        $openwebuiRunning = (docker inspect open-webui --format "{{.State.Status}}" 2>$null) -eq "running"
        if ($openwebuiRunning) {
            Write-Host "Registering Open WebUI model memory loading indicator filter..." -ForegroundColor Cyan
            $pythonCmd = if (Get-Command python -ErrorAction SilentlyContinue) { "python" } elseif (Get-Command py -ErrorAction SilentlyContinue) { "py" } else { $null }
            if ($pythonCmd) {
                & $pythonCmd "$PSScriptRoot\config\open-webui\register_filter.py" >$null 2>&1
            }
            Write-Host "  * Open WebUI 'Loading model into memory...' UI indicator is active." -ForegroundColor Green
        }
    }

    $domain = "spicy-llama.duckdns.org"
    if (Test-Path "$PSScriptRoot\.env") {
        $envLines = Get-Content "$PSScriptRoot\.env"
        foreach ($line in $envLines) {
            if ($line -match '^\s*DOMAIN_NAME\s*=\s*(.+)$') {
                $val = $matches[1].Trim()
                if ($val) { $domain = $val }
            }
        }
    }

    Write-Host ""
    Write-Host "=====================================================" -ForegroundColor Green
    Write-Host "     [SUCCESS] All Homelab Services Are Up & Running " -ForegroundColor Green
    Write-Host "=====================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "  --- Homelab Service Endpoints (DuckDNS HTTPS) ---" -ForegroundColor Cyan
    Write-Host "  * Central Portal: https://$domain" -ForegroundColor Yellow
    Write-Host "  * Jellyfin:       https://jellyfin.$domain" -ForegroundColor White
    Write-Host "  * Jellyseerr:     https://seerr.$domain" -ForegroundColor White
    Write-Host "  * Jellystat:      https://stat.$domain" -ForegroundColor White
    Write-Host "  * Sonarr:         https://sonarr.$domain" -ForegroundColor White
    Write-Host "  * Radarr:         https://radarr.$domain" -ForegroundColor White
    Write-Host "  * Prowlarr:       https://prowlarr.$domain" -ForegroundColor White
    Write-Host "  * Bazarr:         https://bazarr.$domain" -ForegroundColor White
    Write-Host "  * Maintainerr:    https://maintainerr.$domain" -ForegroundColor White
    Write-Host "  * qBittorrent:    https://qbit.$domain" -ForegroundColor White
    Write-Host "  * FlareSolverr:   https://flaresolverr.$domain" -ForegroundColor White
    if ($aiEnabled) {
        Write-Host "  * Open WebUI (AI):https://ai.$domain" -ForegroundColor White
        Write-Host "  * Perplexica:     https://research.$domain (Tailscale/LAN only)" -ForegroundColor White
    }
    Write-Host ""
    Write-Host "  --- Direct Port Fallbacks (Localhost) ---" -ForegroundColor DarkGray
    Write-Host "  * Dashboard:      http://localhost:3000" -ForegroundColor DarkGray
    Write-Host "  * Jellyfin:       http://localhost:8096" -ForegroundColor DarkGray
    Write-Host "  * Jellyseerr:     http://localhost:5055" -ForegroundColor DarkGray
    Write-Host "  * qBittorrent:    http://localhost:8080   (via Gluetun VPN)" -ForegroundColor DarkGray
    if ($aiEnabled) {
        Write-Host "  * Open WebUI:     http://localhost:3080" -ForegroundColor DarkGray
        Write-Host "  * Perplexica:     http://localhost:3001" -ForegroundColor DarkGray
    }
    Write-Host ""
}