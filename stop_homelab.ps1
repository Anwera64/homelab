# Stop_Media_Stack.ps1

# 1. Auto-elevate script to run as Administrator
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
    exit
}

Write-Host "Stopping Media Stack..." -ForegroundColor Red

# 2. Stop Windows Services (if registered as services)
$serviceNames = @("Sonarr", "Radarr", "Prowlarr", "JellyfinServer")
foreach ($service in $serviceNames) {
    Get-Service -Name $service -ErrorAction SilentlyContinue | Stop-Service -Force
}

# 3. Force-kill processes (including Jellyfin Server and Tray apps)
$processNames = @("Sonarr", "Radarr", "Prowlarr", "qbittorrent", "jellyfin", "jellyfin-tray", "Jellyfin.Windows.Tray", "flaresolverr")
foreach ($proc in $processNames) {
    Stop-Process -Name $proc -ErrorAction SilentlyContinue -Force
}

# 4. Stop FlareSolverr if running via Docker
if (Get-Command docker -ErrorAction SilentlyContinue) {
    docker stop flaresolverr 2>$null
}

Write-Host "All media services, processes, and containers have been stopped!" -ForegroundColor Green
Start-Sleep -Seconds 3