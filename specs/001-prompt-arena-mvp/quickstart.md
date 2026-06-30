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
```

A provider with no key configured is treated as unavailable and surfaces a per-provider error
(it never breaks the others — FR-010).

Tunable limits (finalized in implementation): `MAX_PROMPT_LEN`, per-provider timeout
(`PROVIDER_TIMEOUT_MS`).

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
   provider responds, independently (a fast provider shows before a slow one). Panels begin
   populating within ~2s of submission (SC-001).
4. Try to select a 5th provider → **blocked** with the limit communicated.
5. Submit with empty prompt or zero providers → **blocked** with a validation message.

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
