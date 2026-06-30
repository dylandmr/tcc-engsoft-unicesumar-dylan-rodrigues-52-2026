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

Create and run a comparison. Validates input, fans out to the selected providers concurrently, and
returns the created comparison id. Requires auth.

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

### GET /api/comparisons/{id}/stream

Server-Sent Events stream of per-provider results for a comparison the caller owns (FR-008, FR-009,
FR-010). Requires auth. `Content-Type: text/event-stream`.

Events (each `data:` payload is JSON; events are tagged with `event:` name):

- `event: result` — one provider finished (success/empty/error/timeout):
  ```json
  {
    "provider": "CLAUDE",
    "outcome": "SUCCESS",
    "responseText": "...",
    "errorMessage": null,
    "responseTimeMs": 1840
  }
  ```
  `outcome` ∈ {`SUCCESS`,`EMPTY`,`ERROR`,`TIMEOUT`} (FR-011, FR-012, FR-013). For `ERROR`/`TIMEOUT`,
  `errorMessage` is set and `responseText` is null.
- *(optional, if token streaming is implemented)* `event: token` — incremental chunk:
  ```json
  { "provider": "CLAUDE", "chunk": "..." }
  ```
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
      { "provider": "CLAUDE",  "outcome": "SUCCESS", "responseText": "...", "errorMessage": null, "responseTimeMs": 1840 },
      { "provider": "CHATGPT", "outcome": "SUCCESS", "responseText": "...", "errorMessage": null, "responseTimeMs": 2110 },
      { "provider": "GEMINI",  "outcome": "ERROR",   "responseText": null,  "errorMessage": "rate_limited", "responseTimeMs": null }
    ]
  }
  ```
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
| GET /api/comparisons/{id}/stream | FR-008, FR-009, FR-010, FR-011, FR-012, FR-013 |
| GET /api/comparisons | FR-015, FR-016, FR-017 |
| GET /api/comparisons/{id} | FR-014, FR-015, FR-016 |
| (all protected) | FR-001, FR-016; secrets server-side FR-018 |
