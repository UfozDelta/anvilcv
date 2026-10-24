#!/usr/bin/env bash
# Redeploys ResuForge: build the new image, then swap the stack onto it.
# Run manually on the server from the repo root, or via the Deploy workflow (which fast-forwards to the commit first, making the pull a no-op).
set -euo pipefail

echo "==> Pulling latest code"
git pull --ff-only

# Building before stopping keeps the old stack serving during the multi-minute build;
# `up -d` then recreates only the services whose image changed.
echo "==> Building app image"
docker compose build

echo "==> Starting stack"
docker compose up -d

echo "==> Pruning dangling images"
docker image prune -f

echo "==> Done"
docker compose ps
