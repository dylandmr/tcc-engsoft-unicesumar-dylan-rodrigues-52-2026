# Prompt Arena — one-command local launch (Windows PowerShell).
# Creates .env from the template if missing, checks Docker, then builds & runs the app.
# Open http://localhost:8080 and sign in with demo / demo1234.
# See docs/LOCAL_SETUP.md for the full walkthrough (provider keys, validation, etc.).

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path "$root\.env")) {
    Copy-Item "$root\.env.example" "$root\.env"
    Write-Host "Created .env from .env.example." -ForegroundColor Yellow
    Write-Host "Open .env and paste at least one provider API key (Gemini is free)," -ForegroundColor Yellow
    Write-Host "then re-run this script. See docs/LOCAL_SETUP.md section 2." -ForegroundColor Yellow
    exit 0
}

try {
    docker version *> $null
} catch {
    Write-Error "Docker does not appear to be running. Start Docker Desktop and retry."
    exit 1
}

Write-Host "Building and starting Prompt Arena -> http://localhost:8080 (Ctrl+C to stop)" -ForegroundColor Cyan
docker compose up --build
