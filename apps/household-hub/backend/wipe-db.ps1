# wipe-db.ps1
# Convenience wrapper for .\scripts\wipe-db.ps1
param(
    [switch]$Force,
    [switch]$Restart,
    [switch]$NoRestart
)

& "$PSScriptRoot\scripts\wipe-db.ps1" @PSBoundParameters
