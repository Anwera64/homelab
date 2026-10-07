# enable_virtualization.ps1
# Enables Windows Virtual Machine Platform, WSL2, and Windows Hypervisor for Docker Desktop

# Auto-elevate to Administrator
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Requesting Administrator permissions..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
    exit
}

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "       Enabling Windows Virtualization & WSL2        " -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan

# 1. Enable Virtual Machine Platform
Write-Host "1. Enabling Virtual Machine Platform..." -ForegroundColor Yellow
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart

# 2. Enable Windows Subsystem for Linux
Write-Host "2. Enabling Windows Subsystem for Linux..." -ForegroundColor Yellow
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart

# 3. Enable Hypervisor launch in BCD
Write-Host "3. Setting Hypervisor launch to Auto..." -ForegroundColor Yellow
bcdedit /set hypervisorlaunchtype auto

# 4. Update WSL kernel to latest
Write-Host "4. Updating WSL kernel..." -ForegroundColor Yellow
wsl --update --web-download

Write-Host ""
Write-Host "=====================================================" -ForegroundColor Green
Write-Host " [SUCCESS] Virtualization features enabled!          " -ForegroundColor Green
Write-Host " A system restart is REQUIRED to activate changes.   " -ForegroundColor Yellow
Write-Host "=====================================================" -ForegroundColor Green
Write-Host ""
$choice = Read-Host "Would you like to restart your PC now? (Y/N)"
if ($choice -match "^[Yy]") {
    Write-Host "Restarting PC in 5 seconds..." -ForegroundColor Red
    Restart-Computer -Force
}
