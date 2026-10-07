# startup_homelab.ps1
# Starts the desktop's stack (Ollama and the log shipper) and makes sure the
# models in config/ollama-models/models.json are there. Everything else runs
# on the server.

# The script is on PATH and run from anywhere: work from its own folder.
if ($PSScriptRoot) {
    Set-Location -Path $PSScriptRoot
}
# config/ stays in the repo root, shared with the other hosts.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "         [+] Starting the Desktop Stack              " -ForegroundColor Cyan
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
}

$ollamaConfig = Join-Path $repoRoot "config\ollama"
if (-not (Test-Path $ollamaConfig)) {
    New-Item -ItemType Directory -Path $ollamaConfig -Force >$null
}

# 4. Start the stack. A version bumped in the compose file is fetched here.
Write-Host "Starting Ollama and the log shipper..." -ForegroundColor Cyan
docker compose -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Docker compose encountered an error during service startup." -ForegroundColor Red
    Write-Host "  -> Check logs with: docker compose logs" -ForegroundColor Yellow
}

# 5. Post-Startup Container Health Audit
Write-Host "Verifying container health statuses..." -ForegroundColor DarkGray
Start-Sleep -Seconds 2

$failedContainers = @()
$containerIds = docker compose -f "$PSScriptRoot\docker-compose.yml" --env-file "$PSScriptRoot\.env" ps -a -q 2>$null
if ($containerIds) {
    foreach ($cId in $containerIds) {
        $cName = (docker inspect $cId --format "{{.Name}}").TrimStart('/')
        $status = docker inspect $cId --format "{{.State.Status}}"
        $restarting = docker inspect $cId --format "{{.State.Restarting}}"
        $exitCode = docker inspect $cId --format "{{.State.ExitCode}}"
        $health = docker inspect $cId --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}"

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
    $ollamaRunning = (docker inspect ollama --format "{{.State.Status}}" 2>$null) -eq "running"
    if ($ollamaRunning) {
        Write-Host "Checking local AI models in Ollama..." -ForegroundColor Cyan
        # The manifest names the models; the backend's DEFAULT_LLM_MODEL picks which one agents use.
        $manifest = Get-Content (Join-Path $repoRoot "config\ollama-models\models.json") -Raw | ConvertFrom-Json
        $installedModelsRaw = (docker exec ollama ollama list 2>$null) -join "`n"
        $importsDir = Join-Path $repoRoot "config\ollama\imports"
        New-Item -ItemType Directory -Force -Path $importsDir | Out-Null
        foreach ($model in $manifest.models) {
            $name = $model.name
            if ($installedModelsRaw -match "(?m)^$([regex]::Escape($name))(:latest)?\s") {
                Write-Host "  * Model '$name' is ready." -ForegroundColor Green
                continue
            }

            Write-Host "  [+] Downloading model '$name' into the ollama_models volume (this can take a while)..." -ForegroundColor Yellow
            if ($model.ollama_pull) {
                # From the Ollama library, which verifies what it downloads; the Modelfile builds on it.
                docker exec ollama ollama pull $($model.ollama_pull)
                if ($LASTEXITCODE -ne 0) {
                    Write-Host "  [WARNING] Pull of '$($model.ollama_pull)' failed; it will be retried on the next start." -ForegroundColor Yellow
                    continue
                }
                Copy-Item -Path (Join-Path $repoRoot "config\ollama-models\$($model.modelfile)") -Destination (Join-Path $importsDir $model.modelfile) -Force
                docker exec ollama ollama create $name -f "/root/.ollama/imports/$($model.modelfile)"
                if ($LASTEXITCODE -eq 0) {
                    Write-Host "  [SUCCESS] Model '$name' is ready!" -ForegroundColor Green
                } else {
                    Write-Host "  [WARNING] Ollama could not register '$name'; it will be retried on the next start." -ForegroundColor Yellow
                }
                continue
            }

            $gguf = Join-Path $importsDir "$name.gguf"
            curl.exe -fL --retry 5 -C - -o $gguf $model.gguf_url
            if ($LASTEXITCODE -ne 0) {
                Write-Host "  [WARNING] Download of '$name' failed; it will be retried on the next start." -ForegroundColor Yellow
                continue
            }

            $hash = (Get-FileHash -Path $gguf -Algorithm SHA256).Hash.ToLower()
            if ($hash -ne $model.sha256) {
                Write-Host "  [WARNING] '$name' does not match its SHA256 ($hash); discarding it." -ForegroundColor Yellow
                Remove-Item $gguf -Force -ErrorAction SilentlyContinue
                continue
            }

            Copy-Item -Path (Join-Path $repoRoot "config\ollama-models\$($model.modelfile)") -Destination (Join-Path $importsDir $model.modelfile) -Force
            docker exec ollama ollama create $name -f "/root/.ollama/imports/$($model.modelfile)"
            $created = $LASTEXITCODE -eq 0
            Remove-Item $gguf -Force -ErrorAction SilentlyContinue
            if ($created) {
                Write-Host "  [SUCCESS] Model '$name' is ready!" -ForegroundColor Green
            } else {
                Write-Host "  [WARNING] Ollama could not register '$name'; it will be retried on the next start." -ForegroundColor Yellow
            }
        }
    }

    Write-Host ""
    Write-Host "=====================================================" -ForegroundColor Green
    Write-Host "     [SUCCESS] Ollama and the log shipper are up     " -ForegroundColor Green
    Write-Host "=====================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "  The media services, the Hub and the dashboard run on the server:" -ForegroundColor Cyan
    Write-Host "  https://home.spicy-llama.duckdns.org" -ForegroundColor Yellow
    Write-Host ""
}
