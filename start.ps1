#Requires -Version 5.1
<#
.SYNOPSIS
  Starts ResuForge. Backend and UI both served on http://localhost:8080.
.PARAMETER Frontend
  Force a frontend rebuild even if it looks up to date.
.PARAMETER NoBrowser
  Do not open the browser once the app is ready.
#>
param(
    [switch]$Frontend,
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$config = 'src/main/resources/application-local.yml'
$static = 'src/main/resources/static/index.html'

# --- preflight -------------------------------------------------------------
if (-not (Test-Path $config)) {
    Write-Host "Missing $config" -ForegroundColor Red
    Write-Host "Create it with your Neon URL, LLM key, tectonic path, and seed password. See README."
    exit 1
}

$tectonic = (Select-String -Path $config -Pattern '^\s*binary:\s*(.+)$').Matches.Groups[1].Value.Trim()
if ($tectonic -and -not (Test-Path $tectonic)) {
    Write-Host "tectonic not found at: $tectonic" -ForegroundColor Yellow
    Write-Host "PDF export will fail. Everything else works. Fix tectonic.binary in $config."
}

$held = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($held) {
    $proc = Get-Process -Id $held.OwningProcess
    Write-Host "Port 8080 is in use by PID $($proc.Id) ($($proc.ProcessName))." -ForegroundColor Red
    Write-Host "If that is a leftover ResuForge run: Stop-Process -Id $($proc.Id) -Force"
    exit 1
}

# --- frontend --------------------------------------------------------------
# Vite writes into src/main/resources/static, so Spring serves the UI from the
# same origin as the API. Rebuild only when sources are newer than the output.
$stale = $true
if ((-not $Frontend) -and (Test-Path $static)) {
    $newestSrc = (Get-ChildItem frontend/src -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
    $built     = (Get-Item $static).LastWriteTime
    if ($built -gt $newestSrc) { $stale = $false }
}

if ($stale) {
    Write-Host "Building frontend..." -ForegroundColor Cyan
    if (-not (Test-Path frontend/node_modules)) {
        Push-Location frontend; npm install; Pop-Location
        if ($LASTEXITCODE -ne 0) { Write-Host "npm install failed" -ForegroundColor Red; exit 1 }
    }
    Push-Location frontend; npm run build; Pop-Location
    if ($LASTEXITCODE -ne 0) { Write-Host "Frontend build failed" -ForegroundColor Red; exit 1 }
} else {
    Write-Host "Frontend up to date (use -Frontend to force a rebuild)." -ForegroundColor DarkGray
}

# --- browser ---------------------------------------------------------------
# Backend runs in the foreground so Ctrl-C stops it, so the browser has to be
# opened from a side job that waits for the port to answer.
if (-not $NoBrowser) {
    Start-Job -ScriptBlock {
        for ($i = 0; $i -lt 120; $i++) {
            Start-Sleep -Seconds 1
            try {
                Invoke-WebRequest -Uri 'http://localhost:8080/' -UseBasicParsing -TimeoutSec 2 | Out-Null
                Start-Process 'http://localhost:8080'
                return
            } catch { }
        }
    } | Out-Null
}

# --- backend ---------------------------------------------------------------
Write-Host "Starting backend on http://localhost:8080 (Ctrl-C to stop)..." -ForegroundColor Cyan
$env:SPRING_PROFILES_ACTIVE = 'local'
mvn spring-boot:run
