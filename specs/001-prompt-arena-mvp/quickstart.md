# Quickstart & Validation: Prompt Arena MVP

**Feature**: 001-prompt-arena-mvp
**Purpose**: How to run the local prototype and validate the feature end-to-end. This is a run/
validation guide — implementation detail lives in `tasks.md` and the code itself.

See also: [spec.md](./spec.md) · [plan.md](./plan.md) · [research.md](./research.md) ·
[data-model.md](./data-model.md) · [contracts/rest-api.md](./contracts/rest-api.md)

## Prerequisites

- **Docker** + Docker Compose (the only requirement to run the prototype).
- For local (non-Docker) development: **JDK 21**, **Maven**, **Node.js 20+**.
- Provider API keys for any provider you want to exercise live (Gemini, OpenAI, Claude, Grok,
  DeepSeek). Tests run **without** keys (mocked).

## Configuration

Provider keys and secrets are supplied via environment variables / a local `.env` that is **git-
ignored** (Constitution IV; FR-018). Expected variables (names finalized in implementation):

```text
OPENAI_API_KEY=...        # ChatGPT
ANTHROPIC_API_KEY=...     # Claude
GOOGLE_API_KEY=...        # Gemini
XAI_API_KEY=...           # Grok   (OpenAI-compatible, base https://api.x.ai/v1)
DEEPSEEK_API_KEY=...      # DeepSeek (OpenAI-compatible, base https://api.deepseek.com)

# Per-provider model id (each provider calls exactly one configured model; defaults applied if unset)
OPENAI_MODEL=...
ANTHROPIC_MODEL=...
GOOGLE_MODEL=...
XAI_MODEL=...
DEEPSEEK_MODEL=...
```

A provider with no key configured is treated as unavailable and surfaces a per-provider error
(it never breaks the others — FR-010).

Tunable limits (prototype defaults; override via env): `MAX_PROMPT_LEN` = **8000** characters,
per-provider timeout `PROVIDER_TIMEOUT_MS` = **45000** ms.

## Run the prototype (single command)

```bash
docker compose up
```

This builds and starts the backend (Spring Boot, Java 21) and serves the built SPA same-origin, with
a SQLite database file persisted under a mounted volume (e.g. `./data/app.db`). Open the printed URL
(e.g. `http://localhost:8080`).

A seed user is provisioned for the demo (accounts are created out of band — spec Assumptions). Its
credentials are printed/seeded at startup.

## Validation scenarios

Each scenario maps to user stories and success criteria in [spec.md](./spec.md).

### V1 — Core comparison (User Story 1, SC-001, SC-006)

1. Sign in as the seed user.
2. Enter a prompt, select 3–4 providers, submit.
3. **Expect**: one labeled panel per selected provider, side by side; each panel fills as its
   provider responds, independently (a fast provider shows before a slow one). **Timing check
   (SC-001)**: the panels appear and the SSE stream opens within ~2s of submission (measure
   submit→first `result`/stream-open, excluding provider thinking time).
4. After all providers report → **Expect**: a post-race telemetry summary below the panels ranking
   providers by response time, with first-token latency, token counts, and model id (FR-008/FR-019).
5. Try to select a 5th provider → **blocked** with the limit communicated.
6. Submit with empty prompt or zero providers → **blocked** with a validation message.

### V2 — Isolated failure handling (User Story 2, SC-002)

1. Force one provider to fail (e.g. unset its key or point it at an invalid base URL) and include it
   in a comparison with ≥2 healthy providers.
2. **Expect**: the failing provider's panel shows an error/timeout state; every other panel shows its
   normal response (SC-002). The overall request is **not** reported as failed.
3. Force a provider to exceed `PROVIDER_TIMEOUT_MS` → its panel shows a **timeout** state, others
   unaffected.

### V3 — Authentication gate (User Story 3, SC-004)

1. While signed out, open the comparison or history URL directly → **redirected to sign in**;
   protected content is not shown. Hitting the API directly returns **401** (SC-004).
2. Sign in with wrong credentials → denied with a non-revealing message (FR-003).
3. Sign in correctly → reach the comparison screen. Sign out → protected screens are inaccessible
   again.

### V4 — History, per-user scoping (User Story 4, SC-005, SC-007)

1. Run one or more comparisons, open **History** → past prompts listed with providers and responses,
   newest first. Reopen a specific one in under ~1 min (SC-007).
2. A comparison where some providers failed → its history entry reflects both successes and failures
   (FR-014).
3. Sign in as a **second** user with no history → **empty state** shown (FR-017). The second user
   never sees the first user's comparisons; 0% cross-user leakage (SC-005, FR-016).

## Automated test validation (Constitution III)

Run without live keys/network:

```bash
# Backend (from backend/)
mvn test          # JUnit 5: WireMock provider-adapter tests + Mockito orchestration/isolation tests

# Frontend (from frontend/)
npm test          # Vitest + React Testing Library (+ MSW for mocked SSE/JSON)
```

**Expect**: provider adapter tests pass against WireMock stubs (success/empty/error/timeout/SSE);
fan-out/isolation tests prove one provider's failure does not affect others; auth tests prove
protected routes reject unauthenticated access. Definition of Done requires these passing
(Constitution: Quality Gates).

## Validation results (T062)

**Run date**: 2026-06-30 · **Result**: ✅ all scenarios validated via the automated suite + CI.
Backend **70** tests (JaCoCo **100%**) and frontend **74** tests (Vitest **100%**) pass; the CI
**docker** job builds the combined image, runs `docker compose up`, and asserts the health endpoint
returns 200 (the image runs, not just the unit tests). Each scenario's behaviour is pinned by the
tests below, which run **without live keys** (mocked providers / WireMock / MSW):

| Scenario | Status | Backing evidence (automated) |
|----------|--------|------------------------------|
| **V1** Core comparison | ✅ | `ComparisonServiceTest` (fan-out, all-succeed, results matched) · `ComparisonEndpointTest` (POST→PENDING, list, detail) · frontend `ResultsPage`/`useArena`/`arenaReducer` tests (one lane per provider, independent fill) · `useComposer`/`validation` tests (max-4, empty/zero-provider blocked) |
| **V2** Isolated failures | ✅ | `ComparisonServiceTest` (`oneProviderErrorsWhileOthersSucceed`, `slowProviderTimesOutWhileOthersUnaffected`, `allProvidersFailIndependently`) · `ProviderResultMapper` classification · frontend `ProviderLane`/`laneStatus` distinct error/timeout/empty states |
| **V3** Auth gate | ✅ | `ProtectedRouteTest` (401 when unauthenticated) · `AuthFlowTest` (non-revealing 401, session login, logout) · frontend `LoginPage`/`session`/`ProtectedRoute` tests (redirect, login+logout) |
| **V4** History, per-user scoping | ✅ | `HistoryScopingTest` (own-only list, cross-user 404, empty list — **0% leakage**) · `PersistenceTest` (failed-provider outcomes persisted, `COMPLETE`) · frontend `HistoryPage` tests (list, empty state, reopen) |

**Remaining manual confirmation**: a live walkthrough with real provider API keys (the timing checks
SC-001 ≈ 2 s and SC-007 reopen ≈ 1 min, and real model output in the lanes). Without keys, every
provider surfaces a per-provider "unavailable" error — which itself exercises the V2 isolation path
end-to-end. Add keys to `.env` and run `docker compose up` to perform this pass.
