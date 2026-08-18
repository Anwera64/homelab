# startup_homelab.ps1
# Runs all Arr apps, download clients, and media servers on demand

Write-Host "Initializing Media Stack..." -ForegroundColor Cyan

# 1. Ensure NordVPN is connected and actively routing traffic
$nordPath = "C:\Program Files\NordVPN\NordVPN.exe"

function Test-NordVpnRouted {
    $route = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | Where-Object { 
        $_.InterfaceAlias -like "*Nord*" 
    }
    return ($null -ne $route)
}

if (Test-NordVpnRouted) {
    Write-Host "NordVPN is already connected and routing traffic. Skipping connection wait." -ForegroundColor Green
} elseif (Test-Path $nordPath) {
    Write-Host "NordVPN is disconnected. Connecting to fastest server..." -ForegroundColor Yellow
    # Trigger auto-connect via NordVPN CLI
    & "$nordPath" -c

    Write-Host "Waiting for VPN tunnel and routing to establish..." -ForegroundColor Cyan
    $timeoutSeconds = 12
    $elapsed = 0
    $connected = $false

    while ($elapsed -lt $timeoutSeconds) {
        Start-Sleep -Seconds 1
        $elapsed++
        if (Test-NordVpnRouted) {
            Write-Host "VPN tunnel secured and routed in $elapsed second(s)!" -ForegroundColor Green
            $connected = $true
            break
        }
    }

    if (-not $connected) {
        Write-Host "Warning: VPN connection timed out after $timeoutSeconds seconds. Proceeding..." -ForegroundColor Yellow
    }
} else {
    Write-Host "NordVPN not found at $nordPath. Please verify the installation path." -ForegroundColor Red
}

# 2. Define exact paths for your main applications
$apps = @(
    "C:\ProgramData\Sonarr\bin\Sonarr.exe",
    "C:\ProgramData\Radarr\bin\Radarr.exe",
    "C:\ProgramData\Prowlarr\bin\Prowlarr.exe",
    "C:\Program Files\qBittorrent\qbittorrent.exe",
    "C:\Program Files\Jellyfin\Server\jellyfin-windows-tray\Jellyfin.Windows.Tray.exe"
)

# Launch each media application in the background
foreach ($app in $apps) {
    if (Test-Path $app) {
        Write-Host "Launching $app..." -ForegroundColor DarkGray
        Start-Process -FilePath $app -WindowStyle Minimized
    } else {
        Write-Host "File not found (Skipping): $app" -ForegroundColor Red
    }
}

# 3. Launch FlareSolverr (Standalone Executable or Docker container)
$flarePaths = @(
    "$env:USERPROFILE\Downloads\flaresolverr_windows_x64\flaresolverr\flaresolverr.exe",
    "C:\Program Files\FlareSolverr\flaresolverr.exe",
    "C:\FlareSolverr\flaresolverr.exe"
)
$flareStarted = $false

foreach ($fPath in $flarePaths) {
    if (Test-Path $fPath) {
        Write-Host "Launching FlareSolverr..." -ForegroundColor DarkGray
        Start-Process -FilePath $fPath -WindowStyle Minimized
        $flareStarted = $true
        break
    }
}

if (-not $flareStarted -and (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Starting FlareSolverr Docker container..." -ForegroundColor DarkGray
    docker start flaresolverr 2>$null
    $flareStarted = $true
}

if (-not $flareStarted) {
    Write-Host "FlareSolverr not found. If you moved it, please update its path in this script." -ForegroundColor Yellow
}

Write-Host "Media Stack is up and running safely!" -ForegroundColor Green
Start-Sleep -Seconds 3