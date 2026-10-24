@echo off
REM Redeploys ResuForge after new commits: pull, rebuild the image, swap the app container.
REM Run on the server from the repo root (over ssh is fine).
REM
REM First-time hosting is portal's job (python -m portal deploy <this dir> --host <hostname>). That
REM creates the Cloudflare tunnel, its cloudflared sidecar, DNS, and a compose override. This script
REM only replaces the app container, using the same project name and override so it updates portal's
REM stack in place instead of starting a second one next to it. The tunnel is never touched.
setlocal enabledelayedexpansion

REM portal names the project after the repo folder.
for %%I in (.) do set SLUG=%%~nxI
if defined PORTAL_HOME (set PHOME=%PORTAL_HOME%) else (set PHOME=%USERPROFILE%\.portal)
set OVERRIDE=%PHOME%\projects\%SLUG%\compose.override.yml

if not exist "%OVERRIDE%" (
    echo %OVERRIDE% not found. Deploy once through portal first: python -m portal deploy . --host ^<hostname^>
    exit /b 1
)

echo ==^> Pulling latest code
git pull --ff-only
if errorlevel 1 exit /b 1

REM Building before replacing keeps the old container serving during the multi-minute build;
REM `up -d` then recreates only the app, whose image changed.
echo ==^> Building and starting app
docker compose -p portal-%SLUG% -f compose.yml -f "%OVERRIDE%" up -d --build
if errorlevel 1 exit /b 1

echo ==^> Pruning dangling images
docker image prune -f

echo ==^> Done
docker compose -p portal-%SLUG% -f compose.yml -f "%OVERRIDE%" ps
