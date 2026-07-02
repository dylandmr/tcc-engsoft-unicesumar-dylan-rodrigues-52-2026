#!/usr/bin/env bash
# Prompt Arena — one-command local launch (macOS/Linux).
# Creates .env from the template if missing, checks Docker, then builds & runs the app.
# Open http://localhost:8080 and sign in with demo / demo1234.
# See docs/LOCAL_SETUP.md for the full walkthrough (provider keys, validation, etc.).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example."
  echo "Open .env and paste at least one provider API key (Gemini is free),"
  echo "then re-run this script. See docs/LOCAL_SETUP.md section 2."
  exit 0
fi

if ! docker version >/dev/null 2>&1; then
  echo "Docker does not appear to be running. Start Docker and retry." >&2
  exit 1
fi

echo "Building and starting Prompt Arena -> http://localhost:8080 (Ctrl+C to stop)"
docker compose up --build
