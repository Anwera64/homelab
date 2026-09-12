# dev.ps1
# Boots and stops the Household Hub backend for local development.
# Usage: .\scripts\dev.ps1 start|stop|status|logs
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("start", "stop", "status", "logs")]
    [string]$Command
)

$BackendRoot = Split-Path -Parent $PSScriptRoot
$Python      = Join-Path $BackendRoot ".venv\Scripts\python.exe"
$PidFile     = Join-Path $BackendRoot ".dev-server.pid"
$LogFile     = Join-Path $BackendRoot ".dev-server.log"
$Port        = 3050
$HealthUrl   = "http://127.0.0.1:$Port/api/v1/health"
$HealthTimeoutSeconds = 30

function Get-ServerProcess {
    # The PID file holds the cmd.exe wrapper; checking its name guards against a reused PID.
    if (-not (Test-Path $PidFile)) { return $null }
    $serverPid = (Get-Content $PidFile -Raw).Trim()
    $process = Get-Process -Id $serverPid -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -eq "cmd") { return $process }
    return $null
}

function Get-PortOwner {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) { return $listener.OwningProcess }
    return $null
}

function Test-Health {
    try {
        $response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Start-Server {
    if (Get-ServerProcess) {
        Write-Host "[ERROR] Backend is already running (PID $((Get-ServerProcess).Id)). Use 'stop' first." -ForegroundColor Red
        exit 1
    }
    if (-not (Test-Path $Python)) {
        Write-Host "[ERROR] No virtual environment found at $Python" -ForegroundColor Red
        Write-Host "  -> Create it with: python -m venv .venv; .\.venv\Scripts\pip install -r requirements.txt" -ForegroundColor Yellow
        exit 1
    }
    $portOwner = Get-PortOwner
    if ($portOwner) {
        Write-Host "[ERROR] Port $Port is already in use by PID $portOwner." -ForegroundColor Red
        Write-Host "  -> Inspect it with: Get-Process -Id $portOwner" -ForegroundColor Yellow
        exit 1
    }
    Remove-Item $PidFile -ErrorAction SilentlyContinue

    # cmd.exe merges stdout and stderr into one log file; -u keeps Python output unbuffered.
    $uvicorn = "`"$Python`" -u -m uvicorn app.main:app --reload --host 0.0.0.0 --port $Port"
    $process = Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c `"$uvicorn > `"$LogFile`" 2>&1`"" `
        -WorkingDirectory $BackendRoot `
        -WindowStyle Hidden `
        -PassThru
    $process.Id | Set-Content $PidFile

    Write-Host "Starting backend (PID $($process.Id)), waiting for health check..." -ForegroundColor DarkGray
    $deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($process.HasExited) {
            Remove-Item $PidFile -ErrorAction SilentlyContinue
            Write-Host "[ERROR] Backend exited during startup. Last log lines:" -ForegroundColor Red
            Get-Content $LogFile -Tail 20 -ErrorAction SilentlyContinue
            exit 1
        }
        if (Test-Health) {
            Write-Host "[SUCCESS] Backend is up on port $Port" -ForegroundColor Green
            Write-Host "  * Health:  http://localhost:$Port/api/v1/health" -ForegroundColor White
            Write-Host "  * Docs:    http://localhost:$Port/docs" -ForegroundColor White
            Write-Host "  * Hub:     https://hub.spicy-llama.duckdns.org (via Caddy)" -ForegroundColor White
            Write-Host "  * Logs:    .\scripts\dev.ps1 logs" -ForegroundColor DarkGray
            return
        }
        Start-Sleep -Milliseconds 500
    }
    Write-Host "[WARNING] Backend did not answer $HealthUrl within $HealthTimeoutSeconds s; it is still running." -ForegroundColor Yellow
    Write-Host "  -> Check the logs with: .\scripts\dev.ps1 logs" -ForegroundColor Yellow
}

function Stop-Server {
    $process = Get-ServerProcess
    if (-not $process) {
        Remove-Item $PidFile -ErrorAction SilentlyContinue
        Write-Host "Backend is not running." -ForegroundColor DarkGray
        $portOwner = Get-PortOwner
        if ($portOwner) {
            Write-Host "[WARNING] Port $Port is still held by PID $portOwner, which this script did not start." -ForegroundColor Yellow
        }
        return
    }
    # --reload runs a reloader plus a worker (and the venv launcher spawns the real python),
    # so the whole tree must go or the worker keeps the port.
    taskkill /PID $process.Id /T /F >$null 2>&1
    Remove-Item $PidFile -ErrorAction SilentlyContinue
    Write-Host "[SUCCESS] Backend stopped." -ForegroundColor Green
}

function Show-Status {
    $process = Get-ServerProcess
    if (-not $process) {
        Write-Host "Backend is not running." -ForegroundColor DarkGray
        return
    }
    if (Test-Health) {
        Write-Host "Backend is running (PID $($process.Id)) and healthy on port $Port." -ForegroundColor Green
    } else {
        Write-Host "Backend is running (PID $($process.Id)) but $HealthUrl is not answering." -ForegroundColor Yellow
    }
}

function Show-Logs {
    if (-not (Test-Path $LogFile)) {
        Write-Host "No log file yet. Start the backend first." -ForegroundColor DarkGray
        return
    }
    Get-Content $LogFile -Tail 50 -Wait
}

switch ($Command) {
    "start"  { Start-Server }
    "stop"   { Stop-Server }
    "status" { Show-Status }
    "logs"   { Show-Logs }
}
