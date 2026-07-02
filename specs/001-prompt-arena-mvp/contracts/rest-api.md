# REST API Contract: Prompt Arena MVP

**Feature**: 001-prompt-arena-mvp
**Style**: REST over HTTP (JSON), with one Server-Sent Events (SSE) stream for live comparison
results. All endpoints are same-origin with the SPA. Authentication is a Spring Security session
cookie; protected endpoints require an authenticated session (FR-001).

Base path: `/api`

## Conventions

- Request/response bodies are JSON unless noted (the stream endpoint is `text/event-stream`).
- Unauthenticated access to a protected endpoint returns **401 Unauthorized** (FR-001).
- Accessing another user's resource returns **404 Not Found** (do not reveal existence) (FR-016).
- Validation failures return **400 Bad Request** with a machine-readable `error` code.
- Timestamps are ISO-8601 UTC.
- **Session & CSRF**: auth is a session cookie. State-changing requests (`POST /auth/login`,
  `POST /auth/logout`, `POST /comparisons`) require a **CSRF token**: the SPA reads it from a
  bootstrap cookie/endpoint and echoes it in the `X-XSRF-TOKEN` header. The SSE endpoint is a
  **GET** (non-state-changing), so it is exempt from CSRF and authenticates via the session cookie
  alone — this is required because the browser `EventSource` API cannot set custom headers.

---

## Authentication

### POST /api/auth/login

Establish a session.

Request:
```json
{ "username": "alice", "password": "••••••••" }
```

Responses:
- **200 OK** — session cookie set.
  ```json
  { "username": "alice" }
  ```
- **401 Unauthorized** — invalid credentials. Message MUST NOT reveal which field was wrong (FR-003).
  ```json
  { "error": "invalid_credentials" }
  ```

### POST /api/auth/logout

End the current session (FR-002). Requires auth.

- **204 No Content** — session invalidated.

### GET /api/auth/me

Return the current session's user; used by the SPA to gate protected screens.

- **200 OK** → `{ "username": "alice" }`
- **401 Unauthorized** → not signed in.

---

## Providers

### GET /api/providers

Describe every supported provider for the composer: whether it is configured (has a server-side
key), its default model, and the models selectable for it (FR-020). The `models` list is the
**union** of a curated (predefined) list maintained by the system and the models the provider's own
API reports as available; the live list is fetched only for configured providers, cached
server-side for a short TTL, and a fetch failure silently degrades to the curated list (never an
error — mirrors FR-010's isolation). `models` always contains `defaultModel`. Requires auth.

- **200 OK**
  ```json
  {
    "providers": [
      {
        "provider": "GEMINI",
        "configured": true,
        "defaultModel": "gemini-2.5-flash",
        "models": ["gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro"],
        "source": "live"
      },
      {
        "provider": "CLAUDE",
        "configured": false,
        "defaultModel": "claude-3-5-sonnet-latest",
        "models": ["claude-3-5-haiku-latest", "claude-3-5-sonnet-latest"],
        "source": "curated"
      }
    ]
  }
  ```
  All five providers are always present, in the canonical order {`GEMINI`,`CHATGPT`,`CLAUDE`,
  `GROK`,`DEEPSEEK`}. `models` is sorted ascending and deduplicated. `source` is `"live"` when the
  provider's list API contributed entries, `"curated"` otherwise (unconfigured provider or fetch
  failure).
- **401 Unauthorized**

---

## Comparisons

### POST /api/comparisons

Create a comparison. Validates input and **persists** the comparison record (prompt, providers,
owner) in a `PENDING` state, then returns its id. This call does **not** dispatch the providers —
the fan-out is triggered lazily when the SSE stream below is opened (see "Fan-out trigger" note).
Requires auth.

Request:
```json
{
  "prompt": "Explain quantum entanglement simply.",
  "providers": ["CLAUDE", "CHATGPT", "GEMINI"],
  "models": { "GEMINI": "gemini-2.5-pro" }
}
```

Validation (FR-005, FR-006, FR-020):
- `prompt`: non-empty, length ≤ MAX_PROMPT_LEN (see quickstart).
- `providers`: 1–4 entries, each from {`GEMINI`,`CHATGPT`,`CLAUDE`,`GROK`,`DEEPSEEK`}, no duplicates.
- `models` (optional, FR-020): map of provider → model id. Each key MUST be one of the selected
  `providers`; each value MUST be in that provider's current `models` set from `GET /api/providers`.
  A provider with no entry runs its `defaultModel`. The resolved model per provider (explicit or
  default) is persisted with the comparison and used when the lazy fan-out dispatches.

Responses:
- **201 Created**
  ```json
  { "comparisonId": "c_01H...", "providers": ["CLAUDE", "CHATGPT", "GEMINI"] }
  ```
- **400 Bad Request** — `{ "error": "empty_prompt" | "no_providers" | "too_many_providers" | "duplicate_provider" | "unknown_provider" | "prompt_too_long" | "unknown_model" | "model_for_unselected_provider" }`
- **401 Unauthorized**

The client then opens the SSE stream below to receive results live.

**Fan-out trigger (resolves design ambiguity)**: provider dispatch is **triggered by opening the SSE
stream**, not by `POST`. This guarantees the client is subscribed before any `result` event is
emitted, so no event is missed. Behavior by comparison state when the stream is opened:

- `PENDING` → run the fan-out now, stream each `result` as it arrives, persist on completion, then
  mark the comparison `COMPLETE`.
- `COMPLETE` (stream re-opened, e.g. from history) → **replay** the persisted results as `result`
  events (no providers are called again).

This makes the stream idempotent and avoids duplicate provider calls or lost events.

### GET /api/comparisons/{id}/stream

Server-Sent Events stream of per-provider results for a comparison the caller owns (FR-008, FR-009,
FR-010). Opening the stream triggers (or replays) the fan-out per the note above. Requires auth.
`Content-Type: text/event-stream`.

Events (each `data:` payload is JSON; events are tagged with `event:` name):

- `event: result` — one provider finished (success/empty/error/timeout):
  ```json
  {
    "provider": "CLAUDE",
    "outcome": "SUCCESS",
    "responseText": "...",
    "errorMessage": null,
    "responseTimeMs": 1840,
    "firstTokenMs": 320,
    "inputTokens": 12,
    "outputTokens": 256,
    "model": "claude-3-5-sonnet-20241022"
  }
  ```
  `outcome` ∈ {`SUCCESS`,`EMPTY`,`ERROR`,`TIMEOUT`} (FR-011, FR-012, FR-013). For `ERROR`/`TIMEOUT`,
  `errorMessage` is set and `responseText` is null.

  Telemetry fields (FR-019, all nullable — recorded only when the provider reports them):
  - `firstTokenMs` — milliseconds from dispatching that provider's call to its first streamed text
    delta (time-to-first-token), measured on the same clock as `responseTimeMs`. Null when no token
    ever streamed (errors, timeouts, unconfigured providers).
  - `inputTokens` / `outputTokens` — the provider's own reported prompt/completion token usage.
  - `model` — the exact model identifier the provider reports as having answered (which may be more
    specific than the configured alias, e.g. a dated snapshot).
- `event: chunk` — one provider's incremental text delta, emitted live while that provider streams
  (each precedes that provider's `result`):
  ```json
  { "provider": "CLAUDE", "delta": "..." }
  ```
  Replaying a `COMPLETE` comparison emits no `chunk` events — only the persisted `result`s.
- `event: done` — all providers have reported; the stream then closes:
  ```json
  { "comparisonId": "c_01H...", "completed": 3 }
  ```

Isolation guarantee (FR-010): a failing/slow provider emits its own `result` (ERROR/TIMEOUT) without
delaying or failing any other provider's `result`. The stream never aborts because one provider
failed.

Responses:
- **200 OK** — `text/event-stream` (events as above).
- **401 Unauthorized**
- **404 Not Found** — comparison does not exist or is not owned by the caller.

> Streaming-vs-aggregate note: if the aggregate fallback (research Decision 3) is chosen instead, this
> endpoint is replaced by the `results` array being returned directly from `POST /api/comparisons`
> once all providers complete. The default contract is the SSE stream.

### GET /api/comparisons

List the caller's past comparisons, newest first (FR-015, FR-017). Requires auth.

- **200 OK**
  ```json
  {
    "comparisons": [
      {
        "id": "c_01H...",
        "prompt": "Explain quantum entanglement simply.",
        "providers": ["CLAUDE", "CHATGPT", "GEMINI"],
        "createdAt": "2026-06-29T18:20:00Z"
      }
    ]
  }
  ```
  Empty history returns `{ "comparisons": [] }` (FR-017) — the SPA renders an empty state.

### GET /api/comparisons/{id}

Full detail of one past comparison the caller owns, including each provider's recorded result
(FR-014, FR-015). Requires auth.

- **200 OK**
  ```json
  {
    "id": "c_01H...",
    "prompt": "Explain quantum entanglement simply.",
    "createdAt": "2026-06-29T18:20:00Z",
    "models": { "CLAUDE": "claude-3-5-sonnet-latest", "CHATGPT": "gpt-4o-mini", "GEMINI": "gemini-2.5-pro" },
    "results": [
      { "provider": "CLAUDE",  "outcome": "SUCCESS", "responseText": "...", "errorMessage": null, "responseTimeMs": 1840, "firstTokenMs": 320,  "inputTokens": 12,   "outputTokens": 256, "model": "claude-3-5-sonnet-20241022" },
      { "provider": "CHATGPT", "outcome": "SUCCESS", "responseText": "...", "errorMessage": null, "responseTimeMs": 2110, "firstTokenMs": 450,  "inputTokens": 12,   "outputTokens": 301, "model": "gpt-4o-mini-2024-07-18" },
      { "provider": "GEMINI",  "outcome": "ERROR",   "responseText": null,  "errorMessage": "rate_limited", "responseTimeMs": null, "firstTokenMs": null, "inputTokens": null, "outputTokens": null, "model": null }
    ]
  }
  ```
  Each result row carries the same nullable telemetry fields as the SSE `result` event
  (`firstTokenMs`, `inputTokens`, `outputTokens`, `model` — FR-019). `models` records the model
  **requested** per provider when the comparison was created (FR-020) — it may be an empty map for
  comparisons persisted before model selection existed. The per-result `model` field remains the
  model the provider **reported** answering (they normally agree, but the reported id can be a more
  specific dated snapshot, and is null when the provider never answered).
- **401 Unauthorized**
- **404 Not Found** — unknown or not owned by caller.

---

## Endpoint → Requirement traceability

| Endpoint | Requirements |
|---|---|
| POST /api/auth/login | FR-002, FR-003 |
| POST /api/auth/logout | FR-002 |
| GET /api/auth/me | FR-001 |
| GET /api/providers | FR-020 |
| POST /api/comparisons | FR-004, FR-005, FR-006, FR-007, FR-020 |
| GET /api/comparisons/{id}/stream | FR-008, FR-009, FR-010, FR-011, FR-012, FR-013, FR-019 |
| GET /api/comparisons | FR-015, FR-016, FR-017 |
| GET /api/comparisons/{id} | FR-014, FR-015, FR-016, FR-019 |
| (all protected) | FR-001, FR-016; secrets server-side FR-018 |
