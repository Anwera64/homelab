#Requires -RunAsAdministrator
# compact_docker_disk.ps1
# Hands space freed inside Docker (removed models, images, volumes) back to Windows.
# Docker Desktop keeps everything in one virtual disk that grows but never shrinks on its own.
# Docker is unavailable for a few minutes while this runs; containers come back when it restarts.

# Always ensure working directory is this repository folder
if ($PSScriptRoot) {
    Set-Location -Path $PSScriptRoot
}

$disk = Join-Path $env:LOCALAPPDATA "Docker\wsl\disk\docker_data.vhdx"
$dockerDesktop = Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "        [~] Compacting the Docker data disk          " -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan

if (-not (Test-Path $disk)) {
    Write-Host "[ERROR] Docker data disk not found at $disk" -ForegroundColor Red
    exit 1
}

$sizeBefore = (Get-Item $disk).Length / 1GB
Write-Host ("Size before: {0:N1} GB" -f $sizeBefore) -ForegroundColor DarkGray

# 1. Mark free blocks as unused inside Docker's VM, or the compaction finds nothing to release
Write-Host "Trimming free space inside the Docker VM..." -ForegroundColor DarkGray
docker run --rm --privileged --pid=host alpine nsenter -t 1 -m -- fstrim -av
if ($LASTEXITCODE -ne 0) {
    Write-Host "  [WARNING] Trim failed; compaction will reclaim less space." -ForegroundColor Yellow
}

# 2. Stop Docker Desktop and WSL so the disk is free to attach
Write-Host "Stopping Docker Desktop and WSL..." -ForegroundColor DarkGray
Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue | Stop-Process -Force
wsl --shutdown
Start-Sleep -Seconds 5

# 3. Compact the disk with diskpart (Optimize-VHD needs Hyper-V, which Windows Home lacks)
Write-Host "Compacting (this can take several minutes)..." -ForegroundColor DarkGray
$diskpartScript = Join-Path $env:TEMP "compact_docker_disk.txt"
@(
    "select vdisk file=`"$disk`""
    "attach vdisk readonly"
    "compact vdisk"
    "detach vdisk"
) | Set-Content -Path $diskpartScript -Encoding ascii
diskpart /s $diskpartScript
$diskpartExit = $LASTEXITCODE
Remove-Item $diskpartScript -ErrorAction SilentlyContinue

$sizeAfter = (Get-Item $disk).Length / 1GB

# 4. Bring Docker back; containers with restart policies start with it
if (Test-Path $dockerDesktop) {
    Write-Host "Starting Docker Desktop..." -ForegroundColor DarkGray
    Start-Process -FilePath $dockerDesktop
}

Write-Host ""
if ($diskpartExit -ne 0) {
    Write-Host "[WARNING] diskpart reported an error (exit $diskpartExit); the disk may not be compacted." -ForegroundColor Yellow
}
Write-Host ("Size before: {0:N1} GB, after: {1:N1} GB, reclaimed: {2:N1} GB" -f $sizeBefore, $sizeAfter, ($sizeBefore - $sizeAfter)) -ForegroundColor Green
