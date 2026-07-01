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
  "providers": ["CLAUDE", "CHATGPT", "GEMINI"]
}
```

Validation (FR-005, FR-006):
- `prompt`: non-empty, length ≤ MAX_PROMPT_LEN (see quickstart).
- `providers`: 1–4 entries, each from {`GEMINI`,`CHATGPT`,`CLAUDE`,`GROK`,`DEEPSEEK`}, no duplicates.

Responses:
- **201 Created**
  ```json
  { "comparisonId": "c_01H...", "providers": ["CLAUDE", "CHATGPT", "GEMINI"] }
  ```
- **400 Bad Request** — `{ "error": "empty_prompt" | "no_providers" | "too_many_providers" | "duplicate_provider" | "unknown_provider" | "prompt_too_long" }`
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
    "results": [
      { "provider": "CLAUDE",  "outcome": "SUCCESS", "responseText": "...", "errorMessage": null, "responseTimeMs": 1840, "firstTokenMs": 320,  "inputTokens": 12,   "outputTokens": 256, "model": "claude-3-5-sonnet-20241022" },
      { "provider": "CHATGPT", "outcome": "SUCCESS", "responseText": "...", "errorMessage": null, "responseTimeMs": 2110, "firstTokenMs": 450,  "inputTokens": 12,   "outputTokens": 301, "model": "gpt-4o-mini-2024-07-18" },
      { "provider": "GEMINI",  "outcome": "ERROR",   "responseText": null,  "errorMessage": "rate_limited", "responseTimeMs": null, "firstTokenMs": null, "inputTokens": null, "outputTokens": null, "model": null }
    ]
  }
  ```
  Each result row carries the same nullable telemetry fields as the SSE `result` event
  (`firstTokenMs`, `inputTokens`, `outputTokens`, `model` — FR-019).
- **401 Unauthorized**
- **404 Not Found** — unknown or not owned by caller.

---

## Endpoint → Requirement traceability

| Endpoint | Requirements |
|---|---|
| POST /api/auth/login | FR-002, FR-003 |
| POST /api/auth/logout | FR-002 |
| GET /api/auth/me | FR-001 |
| POST /api/comparisons | FR-004, FR-005, FR-006, FR-007 |
| GET /api/comparisons/{id}/stream | FR-008, FR-009, FR-010, FR-011, FR-012, FR-013, FR-019 |
| GET /api/comparisons | FR-015, FR-016, FR-017 |
| GET /api/comparisons/{id} | FR-014, FR-015, FR-016, FR-019 |
| (all protected) | FR-001, FR-016; secrets server-side FR-018 |
