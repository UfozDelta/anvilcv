@echo off
REM Redeploys ResuForge: stop stack, pull latest, rebuild image, start stack.
REM Run manually on the server from the repo root.
setlocal enabledelayedexpansion

echo ==^> Stopping stack
docker compose down
if errorlevel 1 exit /b 1

echo ==^> Pulling latest code
git pull
if errorlevel 1 exit /b 1

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
