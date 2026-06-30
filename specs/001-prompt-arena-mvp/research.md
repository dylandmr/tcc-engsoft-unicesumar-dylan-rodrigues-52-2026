# Research: Prompt Arena MVP

**Feature**: 001-prompt-arena-mvp
**Date**: 2026-06-29
**Purpose**: Resolve technical unknowns before design. The stack itself (React+Vite / Java Spring
Boot / SQLite / Docker, local) is fixed by the constitution and the TCC document; this research
resolves *how* to realize it.

> Version note: SDK patch versions move weekly. Treat the versions below as "latest verified at
> planning time — pin against Maven Central / npm before building."

## Decision 1 — Provider SDK strategy (uniform interface over 3 client libraries)

**Decision**: Define one internal `LlmProvider` interface returning a uniform result DTO, backed by
**three** client libraries for the five providers:

- **OpenAI Java SDK** (`com.openai:openai-java`) instantiated three times with different `baseUrl` +
  key + model: OpenAI ChatGPT, xAI Grok (`https://api.x.ai/v1`), DeepSeek (`https://api.deepseek.com`).
- **Anthropic Java SDK** (`com.anthropic:anthropic-java`) for Claude.
- **Google GenAI Java SDK** (`com.google.genai:google-genai`) for Gemini.

**Rationale**: xAI Grok and DeepSeek have **no official Java SDK** but are OpenAI-chat-completions
compatible, so one well-tested OpenAI adapter covers three providers. Anthropic and Google have
official Java SDKs. This satisfies Constitution II (uniform interface, no SDK types leaking past the
adapter layer) with the least integration code (Constitution V, YAGNI).

**Per-provider model (resolves underspecification)**: each provider targets exactly one configured
model id, supplied via env so the comparison is explicit and reproducible. The `ProviderRegistry`
reads `{base URL, API key, model}` per provider:

| Provider | Key env | Model env | Base URL |
|---|---|---|---|
| ChatGPT | `OPENAI_API_KEY` | `OPENAI_MODEL` | default OpenAI |
| Grok | `XAI_API_KEY` | `XAI_MODEL` | `https://api.x.ai/v1` |
| DeepSeek | `DEEPSEEK_API_KEY` | `DEEPSEEK_MODEL` | `https://api.deepseek.com` |
| Claude | `ANTHROPIC_API_KEY` | `ANTHROPIC_MODEL` | default Anthropic |
| Gemini | `GOOGLE_API_KEY` | `GOOGLE_MODEL` | default Google |

Each `*_MODEL` has a sensible default constant; verify exact model ids against the provider's current
catalog at build time.

**Alternatives considered**:
- *Five distinct official SDKs* — not possible; two don't exist for Java.
- *Spring AI (`org.springframework.ai`)* — would unify all five behind one `ChatModel`. Viable, but
  adds a sizeable framework with its own opinions; the constitution wants *our* uniform interface.
  Deferred — may be adopted later strictly as an implementation detail behind `LlmProvider`.

## Decision 2 — Concurrent fan-out with per-provider isolation (Java 21)

**Decision**: `CompletableFuture.supplyAsync(...)` over a **virtual-thread executor**
(`Executors.newVirtualThreadPerTaskExecutor()`), each future with `.orTimeout(perProviderLimit)`
then `.exceptionally(ex -> ProviderResult.failure(...))` so every future resolves to a success **or**
a structured failure. No join ever propagates one provider's failure to another (Constitution II).

**Rationale**: Provider calls are blocking I/O; virtual threads (stable in Java 21, JEP 444) make a
slow provider park a cheap thread rather than starve a pool. Putting `.exceptionally` after
`.orTimeout` guarantees isolation and maps timeouts/errors to per-provider `TIMEOUT`/`ERROR`
outcomes (FR-010, FR-011, FR-012).

**Alternatives considered**:
- *Structured concurrency (`StructuredTaskScope`)* — cleanest model but **preview** in Java 21
  (requires `--enable-preview`); revisit when final.
- *Bounded platform thread pool* — must be sized ≥ N; virtual threads remove that concern. Use a
  bounded pool only later if outbound concurrency must be capped for cost/rate limits.
- *Reactor/WebFlux* — paradigm shift that fights the blocking SDKs; overkill for the prototype.

## Decision 3 — Streaming to the frontend (Server-Sent Events)

**Decision**: Stream results with **Server-Sent Events** via Spring MVC `SseEmitter`. The submit
endpoint fans out (Decision 2) and pushes events tagged with `providerId` (`token`, `done`, `error`)
as each provider yields. The React client consumes them (native `EventSource`, or `fetch` +
`ReadableStream` if a POST body/headers are needed) and routes each event to its matching panel.

**Rationale**: This is the simplest mechanism that lets each panel fill **independently as data
arrives** (FR-009) — the core UX. SSE is one-way server→client, exactly the data flow needed. All
three client libraries support token streaming. Drive emitters on virtual threads so blocking stream
reads don't tie up request threads; always `complete()`/`completeWithError()` per stream.

**Fan-out trigger (resolves design ambiguity)**: the fan-out is triggered **when the SSE stream is
opened**, not on `POST /comparisons`. `POST` only validates and persists the comparison in `PENDING`
state and returns its id; opening the stream runs the orchestrator and streams results, persisting
on completion and marking it `COMPLETE`. Re-opening a `COMPLETE` comparison's stream **replays**
persisted results without re-calling providers. This guarantees the client is subscribed before any
event is emitted (no lost events) and keeps provider calls idempotent.

**Auth on the SSE endpoint**: the stream is a `GET` and authenticates via the session cookie only —
the browser `EventSource` API cannot set custom headers, so CSRF is applied to state-changing POSTs
but not to this GET.

**Alternatives considered**:
- *Single aggregated JSON after all complete* — trivially simple, no SSE plumbing, but defeats
  FR-009 (panels would all appear at once). Kept only as a fallback if SSE proves troublesome on the
  timeline.
- *WebFlux `Flux<ServerSentEvent>`* — idiomatic reactive streaming, but pulls the whole app into the
  reactive stack against blocking SDKs.
- *WebSockets* — bidirectional and heavier; unnecessary for server→client only.

## Decision 4 — Authentication (Spring Security, session-based)

**Decision**: Spring Security with **server-side session** authentication (session cookie). A
form/JSON login endpoint establishes the session; logout invalidates it. All comparison and history
routes are protected and scoped to the authenticated user. Passwords stored as **BCrypt** salted
hashes. Provider API keys live only in server-side environment configuration, never sent to the
client.

**Rationale**: The constitution mandates session authentication on every protected route and history
record, scoped per user, with secrets server-side (Constitution IV; FR-001, FR-016, FR-018).
Session cookies are simplest for a same-origin SPA. To keep cookies same-origin, the production
Docker image serves the built SPA from (or behind the same origin as) the backend; in dev, Vite
proxies API calls to Spring Boot. CSRF protection enabled for state-changing requests; accounts are
seeded out of band (no public registration in scope).

**Alternatives considered**:
- *JWT/stateless tokens* — unnecessary for a single local prototype and easy to mishandle in the
  browser; sessions are simpler and match the constitution's wording.

## Decision 5 — SQLite with Spring Boot + JPA

**Decision**: Spring Data JPA on SQLite via:
- JDBC driver `org.xerial:sqlite-jdbc` (~`3.50.x`), driver class `org.sqlite.JDBC`.
- Dialect `org.hibernate.community.dialect.SQLiteDialect` from
  `org.hibernate.orm:hibernate-community-dialects`.
- `spring.datasource.url=jdbc:sqlite:./data/app.db`.
- **WAL mode** (`PRAGMA journal_mode=WAL`) + `busy_timeout`, and a **single-writer** posture:
  HikariCP `maximum-pool-size=1` (safe default for SQLite under concurrent writes).
- Schema via `spring.jpa.hibernate.ddl-auto=update` for the prototype (Flyway optional later).

**Rationale**: SQLite is single-writer; the concurrent fan-out persists multiple `ProviderResult`s,
so WAL + busy timeout + a serialized writer avoid `SQLITE_BUSY`. Suits the local-prototype scope
(Constitution V). Persist the comparison + results from a single writer path after fan-out completes
(or stream-then-persist) to minimize write contention.

**Gotchas to verify during build**: type affinity for booleans/timestamps/UUIDs; `@GeneratedValue`
strategy under the community dialect; limited `ALTER TABLE` for migrations.

## Decision 6 — Testing strategy

**Decision**:
- **Provider adapter tests**: **WireMock** stubbing the HTTP layer (point each client's `baseUrl` at
  WireMock), covering success, empty, 4xx/5xx errors, delays/timeouts, and SSE/streaming bodies.
  Runs with a throwaway key, no network (Constitution III).
- **Orchestration tests**: **Mockito** mocking the `LlmProvider` interface to test fan-out,
  aggregation, and partial-failure/timeout isolation logic without HTTP.
- **Frontend**: **Vitest + React Testing Library**, with **MSW** mocking backend SSE/JSON endpoints;
  optional Playwright for one happy-path streaming end-to-end.

**Rationale**: Constitution III requires provider integrations, the router, and auth to be covered by
automated tests that run without live keys/network. WireMock tests real serialization + adapter
mapping; Mockito isolates routing logic. Vitest reuses the Vite config.

**Alternatives considered**: Spring `MockRestServiceServer` (tied to `RestTemplate`, doesn't fit the
OkHttp-based SDKs); `Mokksy/AI-Mocks` (purpose-built for OpenAI/Anthropic mocking — ergonomic option,
but WireMock is the well-known default); Jest (more config friction than Vitest on Vite).

## Resolved Technical Context (summary)

| Aspect | Decision |
|---|---|
| Backend language/runtime | Java 21 (LTS), virtual threads |
| Backend framework | Spring Boot 3.x (Web MVC + Security + Data JPA) |
| Build (backend) | Maven |
| Provider libraries | `com.openai:openai-java` (×3 base URLs), `com.anthropic:anthropic-java`, `com.google.genai:google-genai` |
| Concurrency | `CompletableFuture` over virtual-thread executor + `orTimeout` + `exceptionally` |
| Streaming | Spring MVC `SseEmitter` → React `EventSource` |
| Frontend | React 18 + Vite (TypeScript), `EventSource` for SSE |
| Persistence | SQLite (`sqlite-jdbc` + `hibernate-community-dialects` SQLiteDialect), WAL, pool size 1 |
| Auth | Spring Security session cookie, BCrypt password hashing |
| Testing (backend) | JUnit 5, WireMock (adapters), Mockito (orchestration) |
| Testing (frontend) | Vitest + React Testing Library + MSW |
| Packaging | Docker + `docker compose up` (single command, local) |

**Verify before pinning**: exact latest patch versions of the three SDKs, the Google GenAI streaming
method name, and `sqlite-jdbc` / `hibernate-community-dialects` versions against the chosen Spring
Boot BOM.
