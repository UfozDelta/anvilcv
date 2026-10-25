@echo off
REM Redeploys ResuForge after new commits: pull, rebuild the image, swap the app container.
REM Run on the server from the repo root (over ssh is fine).
REM
REM Works with or without portal:
REM  - portal-hosted (python -m portal deploy <this dir> --host <hostname> was run once): portal owns
REM    the tunnel, DNS and a compose override. This script reuses its project name and override so it
REM    updates that stack in place instead of starting a second one next to it. The tunnel is untouched.
REM  - standalone (no portal override found): plain docker compose with this folder's .env. Set
REM    TUNNEL_TOKEN and COMPOSE_PROFILES=tunnel there and compose runs the cloudflared service too.
setlocal enabledelayedexpansion

REM portal names the project after the repo folder.
for %%I in (.) do set SLUG=%%~nxI
if defined PORTAL_HOME (set PHOME=%PORTAL_HOME%) else (set PHOME=%USERPROFILE%\.portal)
set OVERRIDE=%PHOME%\projects\%SLUG%\compose.override.yml

if exist "%OVERRIDE%" (
    echo ==^> Hosting: portal ^(%SLUG%^)
    set COMPOSE=docker compose -p portal-%SLUG% -f compose.yml -f "%OVERRIDE%"
) else (
    echo ==^> Hosting: standalone compose
    set COMPOSE=docker compose
)

echo ==^> Pulling latest code
git pull --ff-only
if errorlevel 1 exit /b 1

REM Building before replacing keeps the old container serving during the multi-minute build;
REM `up -d` then recreates only the services whose image changed.
echo ==^> Building and starting
%COMPOSE% up -d --build
if errorlevel 1 exit /b 1

echo ==^> Pruning dangling images
docker image prune -f

echo ==^> Done
%COMPOSE% ps
