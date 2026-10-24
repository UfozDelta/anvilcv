@echo off
REM Redeploys ResuForge: pull latest, build the new image, then swap the stack onto it.
REM Run manually on the server from the repo root, or via the Deploy workflow (which fast-forwards
REM to the commit first, making the pull a no-op).
setlocal enabledelayedexpansion

echo ==^> Pulling latest code
git pull --ff-only
if errorlevel 1 exit /b 1

REM Building before stopping keeps the old stack serving during the multi-minute build;
REM `up -d` then recreates only the services whose image changed.
echo ==^> Building app image
docker compose build
if errorlevel 1 exit /b 1

echo ==^> Starting stack
docker compose up -d
if errorlevel 1 exit /b 1

echo ==^> Pruning dangling images
docker image prune -f

echo ==^> Done
docker compose ps
