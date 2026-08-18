# Start_Media_Stack.ps1
# Runs all Arr apps, download clients, and media servers on demand

Write-Host "Starting Media Stack..." -ForegroundColor Green

# 1. Start Services if installed as Windows Services
Get-Service -Name "Sonarr", "Radarr", "Prowlarr" -ErrorAction SilentlyContinue | Start-Service

# 2. Launch GUI Applications Minimized / Background
$apps = @(
    "C:\Program Files\qBittorrent\qbittorrent.exe",
    "C:\Program Files\Jellyfin\Server\jellyfin.exe"
)

foreach ($app in $apps) {
    if (Test-Path $app) {
        Start-Process -FilePath $app -WindowStyle Minimized
    }
}

Write-Host "All media services are running!" -ForegroundColor Green
Start-Sleep -Seconds 3