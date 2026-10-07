# stop_homelab.ps1
# Stops the desktop's stack (Ollama and the log shipper). The models and the
# log positions stay in their volumes.

# The script is on PATH and run from anywhere: work from its own folder.
if ($PSScriptRoot) {
    Set-Location -Path $PSScriptRoot
}

Write-Host "=====================================================" -ForegroundColor Yellow
Write-Host "         [-] Stopping the Desktop Stack              " -ForegroundColor Yellow
Write-Host "=====================================================" -ForegroundColor Yellow

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Docker is not installed or not in PATH." -ForegroundColor Red
    exit 1
}

Write-Host "Stopping Docker containers..." -ForegroundColor DarkGray
docker compose -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" down

Write-Host ""
Write-Host "[SUCCESS] Ollama and the log shipper are stopped. The Hub's chat is off until they start again." -ForegroundColor Green
