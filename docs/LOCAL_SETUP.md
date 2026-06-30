# Running Prompt Arena locally — full walkthrough

This guide takes you from a fresh checkout to **clicking through the running app in your browser**,
including how to generate every API key/token the project can use. Follow it top to bottom.

> **TL;DR** — install Docker, run `scripts/local-up.ps1` (Windows) or `scripts/local-up.sh`
> (macOS/Linux), paste at least one provider key into the generated `.env`, then open
> <http://localhost:8080> and sign in with **`demo` / `demo1234`**.

---

## 0. What needs a token, and what doesn't

| Token | Needed to run the app? | Purpose |
|-------|------------------------|---------|
| **LLM provider keys** (OpenAI / Anthropic / Google / xAI / DeepSeek) | Optional but recommended | Real model responses in the arena. A provider with **no** key shows a per-provider "unavailable" error — which is fine, and actually demonstrates the isolation feature (US2). |
| **SonarCloud `SONAR_TOKEN`** | **No** | A CI-only code-quality gate. It has **nothing to do** with running the app locally. Covered last, in [§6](#6-optional-sonarcloud-token-ci-only). |

You do **not** need all five provider keys. **One** is enough to see real output; mixing one
configured provider with one unconfigured one is the best way to watch the isolation behaviour live.
The cheapest path to a real test is **Google Gemini**, which has a free tier.

---

## 1. Prerequisites

**Recommended path — Docker only:**

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (includes Docker Compose). Start
  it and wait until the whale icon says "running".

That's the only requirement for the recommended path. (For the alternative "developer" path without
Docker, you'd instead need **JDK 21**, **Maven** — bundled via the `./mvnw` wrapper — and
**Node.js 22+**. See [§4b](#4b-alternative-run-without-docker-dev-mode).)

---

## 2. Generate your LLM provider API keys

Create a key for **at least one** provider. Each subsection lists the sign-up page, where the key
lives, the environment variable the app reads, and the default model (override with the matching
`*_MODEL` variable if you want a different one).

> Treat every key like a password. They go only in your local, git-ignored `.env` — never commit
> them. The app reads them **server-side only**; they are never sent to the browser.

### 2.1 Google Gemini — `GOOGLE_API_KEY` (free tier, easiest)

1. Go to **Google AI Studio**: <https://aistudio.google.com/apikey>
2. Sign in with a Google account → **Create API key** (you can create it in a new or existing
   Google Cloud project).
3. Copy the key into `GOOGLE_API_KEY`.
- Default model: `gemini-2.0-flash` (override with `GOOGLE_MODEL`). Base: `generativelanguage.googleapis.com`.

### 2.2 OpenAI ChatGPT — `OPENAI_API_KEY`

1. Go to <https://platform.openai.com/api-keys>
2. Sign in → **Create new secret key** → copy it (shown once).
3. You must have billing/credit set up under **Settings → Billing**.
4. Put the key in `OPENAI_API_KEY`.
- Default model: `gpt-4o-mini` (override with `OPENAI_MODEL`). Base: `api.openai.com/v1`.

### 2.3 Anthropic Claude — `ANTHROPIC_API_KEY`

1. Go to <https://console.anthropic.com/>
2. Sign in → **Settings → API Keys → Create Key** → copy it.
3. Add credit under **Billing** (Claude API is prepaid).
4. Put the key in `ANTHROPIC_API_KEY`.
- Default model: `claude-3-5-sonnet-latest` (override with `ANTHROPIC_MODEL`). Base: `api.anthropic.com`.

### 2.4 xAI Grok — `XAI_API_KEY`

1. Go to <https://console.x.ai/>
2. Sign in → **API Keys → Create API key** → copy it.
3. Add credit/billing for the xAI API.
4. Put the key in `XAI_API_KEY`.
- Default model: `grok-2-latest` (override with `XAI_MODEL`). Base: `api.x.ai/v1` (OpenAI-compatible).

### 2.5 DeepSeek — `DEEPSEEK_API_KEY`

1. Go to <https://platform.deepseek.com/>
2. Sign in → **API keys → Create new API key** → copy it.
3. Add credit (the DeepSeek open platform is prepaid).
4. Put the key in `DEEPSEEK_API_KEY`.
- Default model: `deepseek-chat` (override with `DEEPSEEK_MODEL`). Base: `api.deepseek.com` (OpenAI-compatible).

---

## 3. Create your `.env`

The app reads keys from a git-ignored `.env` file in the **repository root** (next to
`docker-compose.yml`). Compose loads it automatically.

The helper script in [§4](#4-run-the-app-recommended-docker) creates `.env` for you. To do it
manually instead:

```powershell
# Windows PowerShell (from the repo root)
Copy-Item .env.example .env
notepad .env
```

```bash
# macOS / Linux (from the repo root)
cp .env.example .env
${EDITOR:-nano} .env
```

Fill in the provider key(s) you created. Leave the rest blank — blank providers simply report as
unavailable. Example (paste your real key in place of the placeholder):

```dotenv
GOOGLE_API_KEY=paste-your-gemini-key-here
# OPENAI_API_KEY=paste-your-openai-key-here
# ANTHROPIC_API_KEY=...
# (XAI_API_KEY / DEEPSEEK_API_KEY left blank → those lanes will show "unavailable")
```

Full variable reference is in [§7](#7-environment-variable-reference).

---

## 4. Run the app (recommended: Docker)

From the repository root:

```powershell
# Windows
scripts\local-up.ps1
```

```bash
# macOS / Linux
./scripts/local-up.sh
```

The script verifies Docker is running, creates `.env` from `.env.example` if missing, then runs
`docker compose up --build`. Or run it yourself:

```bash
docker compose up --build
```

First build takes a few minutes (it compiles the React SPA and the Spring Boot backend into one
image). When you see the Spring Boot banner and `Started PromptArenaApplication`, it's ready.

**Open <http://localhost:8080>** in your browser.

- The SQLite database is persisted in `./data/app.db` (a Docker volume), so your history survives
  restarts.
- Stop it with `Ctrl+C`, or `docker compose down` from another terminal.

### 4b. Alternative: run without Docker (dev mode)

Two terminals, from the repo root:

```powershell
# Terminal 1 — backend on :8080 (set the keys you want in this shell first)
$env:GOOGLE_API_KEY = "paste-your-gemini-key-here"
cd backend
.\mvnw.cmd spring-boot:run
```

```powershell
# Terminal 2 — frontend dev server on :5173 (proxies /api → :8080)
cd frontend
npm install
npm run dev
```

Then open the Vite URL it prints (usually <http://localhost:5173>). The dev server proxies `/api/*`
to the backend, so the same-origin contract holds. (On macOS/Linux use `export GOOGLE_API_KEY=...`
and `./mvnw spring-boot:run`.)

---

## 5. Validate in the browser

Sign in with the seeded demo account: **username `demo`, password `demo1234`**.

Walk these scenarios (they mirror `specs/001-prompt-arena-mvp/quickstart.md` V1–V4):

1. **Compare (US1).** On the composer, type a prompt (e.g. *"Explain quantum entanglement for a
   12-year-old"*), select **2–4** providers, click **Run**. Watch each provider's lane fill
   independently — a fast model appears before a slow one; lanes show live latency, a token count,
   a copy button, and a "first to respond" badge.
2. **Limits.** Try selecting a 5th provider → blocked. Submit with an empty prompt or no providers →
   inline validation message.
3. **Isolated failures (US2).** Include a provider you **didn't** give a key (e.g. DeepSeek) alongside
   a configured one. Its lane shows an error/timeout state while the others answer normally — the run
   is not a single catastrophic failure.
4. **History (US4).** Open **History** → your past comparisons appear newest-first. Reopen one → it
   replays the saved results (including the failed lanes).
5. **Auth (US3).** Click **sign out** → you're returned to login and protected pages are no longer
   reachable. Visiting `/history` while signed out redirects to `/login`.

If all five behave as described, the prototype is validated end-to-end — you're clear to approve the
PR.

---

## 6. (Optional) SonarCloud token — CI only

This does **not** affect running or testing the app. It only lights up the SonarCloud quality gate
in GitHub Actions (currently it skips gracefully while no token is set). SonarCloud is **free for
public repositories**.

1. Go to <https://sonarcloud.io> → **Log in with GitHub**.
2. **+ → Analyze new project** → authorize SonarCloud for this repository and import it. The project
   is already configured in `sonar-project.properties` as:
   - organization: `dylandmr`
   - projectKey: `dylandmr_tcc-engsoft-unicesumar-dylan-rodrigues-52-2026`
   If SonarCloud assigns different values, update those two lines to match.
3. In the new project: **Administration → Analysis Method → turn OFF "Automatic Analysis"** (we run
   the scan from CI, and the two modes conflict).
4. Generate a token: top-right avatar → **My Account → Security → Generate Token** (name it e.g.
   `prompt-arena-ci`, type *Project Analysis Token* or *Global*), copy it.
5. Add it to GitHub: repo **Settings → Secrets and variables → Actions → New repository secret**,
   name **`SONAR_TOKEN`**, paste the value.
6. Push any commit (or re-run the workflow). The **Quality · SonarCloud** job will now run the scan
   instead of skipping.

---

## 7. Environment variable reference

All optional. Set them in `.env` (Docker) or your shell (dev mode). Blank ⇒ that provider is
unavailable; an unset `*_MODEL` ⇒ the default below.

| Variable | Default | Notes |
|----------|---------|-------|
| `OPENAI_API_KEY` | — | ChatGPT |
| `OPENAI_MODEL` | `gpt-4o-mini` | |
| `ANTHROPIC_API_KEY` | — | Claude |
| `ANTHROPIC_MODEL` | `claude-3-5-sonnet-latest` | |
| `GOOGLE_API_KEY` | — | Gemini (free tier) |
| `GOOGLE_MODEL` | `gemini-2.0-flash` | |
| `XAI_API_KEY` | — | Grok |
| `XAI_MODEL` | `grok-2-latest` | |
| `DEEPSEEK_API_KEY` | — | DeepSeek |
| `DEEPSEEK_MODEL` | `deepseek-chat` | |
| `MAX_PROMPT_LEN` | `8000` | Max prompt characters (also enforced in the UI). |
| `PROVIDER_TIMEOUT_MS` | `45000` | Per-provider timeout before a lane shows `TIMEOUT`. |

---

## 8. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `docker: command not found` / build hangs | Docker Desktop isn't running. Start it and retry. |
| Port 8080 already in use | Stop the other process, or change the host port in `docker-compose.yml` (`"8081:8080"`) and open `:8081`. |
| Every lane says "unavailable" / error | No keys set, or `.env` not in the repo root. Confirm `.env` sits next to `docker-compose.yml` and has at least one non-blank key, then restart. |
| A lane errors with an auth/4xx message | That provider's key is wrong, or the account has no credit. Re-check the key and billing. |
| A lane shows `TIMEOUT` | The model took longer than `PROVIDER_TIMEOUT_MS` (45 s). Raise it in `.env` if needed. |
| Want a clean slate | Stop the app and delete `./data/app.db*` to reset users/history (the demo user re-seeds on startup). |
| Changed `.env` but nothing changed | Env vars are read at startup. Restart: `Ctrl+C` then `docker compose up`. |

---

*See also: [`README.md`](../README.md) · [`specs/001-prompt-arena-mvp/quickstart.md`](../specs/001-prompt-arena-mvp/quickstart.md) · [`.env.example`](../.env.example)*
