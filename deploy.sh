#!/usr/bin/env bash
# Redeploys ResuForge: stop stack, pull latest, rebuild image, start stack.
# Run manually on the server via SSH from the repo root.
set -euo pipefail

echo "==> Stopping stack"
docker compose down

echo "==> Pulling latest code"
git pull

echo "==> Building app image"
docker compose build

echo "==> Starting stack"
docker compose up -d

echo "==> Pruning dangling images"
docker image prune -f

echo "==> Done"
docker compose ps
