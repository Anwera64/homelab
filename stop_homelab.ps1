# stop_homelab.ps1
# Cleanly terminates all Docker containers in the Homelab stack

# Always ensure working directory is this repository folder
if ($PSScriptRoot) {
    Set-Location -Path $PSScriptRoot
}

Write-Host "=====================================================" -ForegroundColor Yellow
Write-Host "         [-] Stopping Homelab Media Stack            " -ForegroundColor Yellow
Write-Host "=====================================================" -ForegroundColor Yellow

# 1. Stop Docker Compose Stack
if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "Stopping Docker containers..." -ForegroundColor DarkGray
    docker compose -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" down
}

# 2. Cleanup any legacy standalone Windows processes if lingering
$processNames = @("Sonarr", "Radarr", "Prowlarr", "qbittorrent", "jellyfin", "jellyfin-tray", "Jellyfin.Windows.Tray", "flaresolverr")
foreach ($proc in $processNames) {
    $found = Get-Process -Name $proc -ErrorAction SilentlyContinue
    if ($found) {
        Write-Host "Terminating legacy host process: $proc" -ForegroundColor DarkGray
        Stop-Process -Name $proc -ErrorAction SilentlyContinue -Force
    }
}

Write-Host ""
Write-Host "[SUCCESS] All Homelab services have been successfully stopped!" -ForegroundColor Green