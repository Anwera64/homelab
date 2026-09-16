# wipe-db.ps1
# Wipes the Household Hub SQLite database and optionally restarts the backend.
# Usage: .\scripts\wipe-db.ps1 [-Force] [-Restart] [-NoRestart]
param(
    [switch]$Force,
    [switch]$Restart,
    [switch]$NoRestart
)

$ErrorActionPreference = "Stop"

$BackendRoot = Split-Path -Parent $PSScriptRoot
$DevScript   = Join-Path $PSScriptRoot "dev.ps1"
$PidFile     = Join-Path $BackendRoot ".dev-server.pid"
$DbFiles     = @(
    (Join-Path $BackendRoot "household_hub.db"),
    (Join-Path $BackendRoot "household_hub.db-shm"),
    (Join-Path $BackendRoot "household_hub.db-wal")
)
$Port        = 3050

function Get-PortOwner {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) { return $listener.OwningProcess }
    return $null
}

if (-not $Force) {
    Write-Host "[CONFIRMATION] This will permanently delete the local SQLite database." -ForegroundColor Yellow
    $response = Read-Host "Are you sure you want to wipe the database? [y/N]"
    if ($response -notmatch "^[Yy]$") {
        Write-Host "Operation cancelled." -ForegroundColor DarkGray
        exit 0
    }
}

# 1. Check if backend is currently running
$wasRunning = $false
$portOwner = Get-PortOwner
if (Test-Path $PidFile -or $portOwner) {
    $wasRunning = $true
    Write-Host "Backend is running. Stopping to release database file locks..." -ForegroundColor DarkGray
    & $DevScript stop
    Start-Sleep -Milliseconds 500

    # Safety check: if port is still held, kill the process tree directly
    $remainingOwner = Get-PortOwner
    if ($remainingOwner) {
        Write-Host "Terminating remaining process holding port $Port (PID $remainingOwner)..." -ForegroundColor DarkGray
        taskkill /PID $remainingOwner /T /F >$null 2>&1
        Start-Sleep -Milliseconds 500
    }
}

# 2. Remove SQLite database and WAL files
$deletedCount = 0
foreach ($file in $DbFiles) {
    if (Test-Path $file) {
        try {
            Remove-Item -Path $file -Force -ErrorAction Stop
            Write-Host "Deleted: $(Split-Path -Leaf $file)" -ForegroundColor DarkGray
            $deletedCount++
        } catch {
            Write-Host "[ERROR] Failed to delete $file. Is another process holding it?" -ForegroundColor Red
            throw $_
        }
    }
}

if ($deletedCount -eq 0) {
    Write-Host "No database files were found to delete." -ForegroundColor DarkGray
} else {
    Write-Host "[SUCCESS] Database files removed successfully." -ForegroundColor Green
}

# 3. Restart server if requested or if it was running previously
$shouldRestart = ($Restart -or $wasRunning) -and (-not $NoRestart)

if ($shouldRestart) {
    Write-Host "Restarting backend server to regenerate database tables..." -ForegroundColor DarkGray
    & $DevScript start
} else {
    Write-Host "Backend is stopped. Run '.\scripts\dev.ps1 start' when you want to restart it." -ForegroundColor DarkGray
}
