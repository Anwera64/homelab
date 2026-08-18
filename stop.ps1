# Stop_Media_Stack.ps1
Write-Host "Stopping Media Stack..." -ForegroundColor Red

# Stop Windows Services
Get-Service -Name "Sonarr", "Radarr", "Prowlarr" -ErrorAction SilentlyContinue | Stop-Service

# Close GUI processes
Stop-Process -Name "qbittorrent", "jellyfin" -ErrorAction SilentlyContinue -Force

Write-Host "Media Stack stopped." -ForegroundColor Red
Start-Sleep -Seconds 3