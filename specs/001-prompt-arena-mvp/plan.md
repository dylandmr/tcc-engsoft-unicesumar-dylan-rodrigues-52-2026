# Implementation Plan: Prompt Arena MVP

**Branch**: `001-prompt-arena-mvp` | **Date**: 2026-06-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-prompt-arena-mvp/spec.md`

## Summary

Prompt Arena is a local-prototype web platform where a signed-in user submits one prompt that is
fanned out concurrently to up to four of five LLM providers (Gemini, ChatGPT, Claude, Grok,
DeepSeek) and sees each provider's response in its own panel, side by side, as it arrives.
Per-provider failures/timeouts are isolated. The platform adds minimal username/password session
auth and a per-user history view.

**Technical approach**: A React + Vite SPA talks to a Java 21 / Spring Boot REST backend. The backend
exposes a uniform `LlmProvider` interface implemented by three client libraries (OpenAI Java SDK
reused for ChatGPT/Grok/DeepSeek via different base URLs, plus the official Anthropic and Google
GenAI SDKs). A comparison fans out via `CompletableFuture` over a virtual-thread executor with
per-call `orTimeout` + `exceptionally`, guaranteeing isolation, and streams results to the SPA over
Server-Sent Events so panels update independently. The fan-out is **triggered when the SSE stream is
opened** (POST only validates/persists the comparison), so no result event can be missed and a
re-opened stream replays persisted results. Auth uses Spring Security sessions with BCrypt;
persistence is SQLite via Spring Data JPA. Everything ships as a Docker image launchable with
`docker compose up`. (See [research.md](./research.md).)

## Technical Context

**Language/Version**: Java 21 (LTS, virtual threads) backend; TypeScript (React 18) frontend

**Primary Dependencies**: Spring Boot 3.x (Web MVC, Security, Data JPA); provider libraries
`com.openai:openai-java` (×3 base URLs), `com.anthropic:anthropic-java`, `com.google.genai:google-genai`;
React 18 + Vite; SSE via Spring MVC `SseEmitter` / browser `EventSource`

**Storage**: SQLite (embedded, single file) via Spring Data JPA — `org.xerial:sqlite-jdbc` +
`org.hibernate.community.dialect.SQLiteDialect`; WAL mode, HikariCP pool size 1

**Testing**: Backend — JUnit 5, WireMock (provider adapters, no live keys/network), Mockito
(orchestration/isolation). Frontend — Vitest + React Testing Library + MSW

**Target Platform**: Local prototype in Docker (`docker compose up`); modern desktop browser

**Project Type**: Web application (frontend SPA + backend REST service)

**Performance Goals**: Panels begin populating within ~2s of submit excluding provider thinking time
(SC-001), asserted in the quickstart V1 timing check; per-provider timeout enforced; demo-scale
concurrency target of **up to ~5 simultaneous users**, not production load (spec Assumptions)

**Per-provider model**: each provider calls a single configured model id, supplied via env
(`OPENAI_MODEL`, `ANTHROPIC_MODEL`, `GOOGLE_MODEL`, `XAI_MODEL`, `DEEPSEEK_MODEL`) with sensible
defaults; the provider registry (`ProviderRegistry`) reads base URL + key + model per provider so
the comparison is reproducible and the model is explicit.

**Tunable defaults**: `MAX_PROMPT_LEN` = 8000 characters; `PROVIDER_TIMEOUT_MS` = 45000 ms. Both are
configurable; these are the prototype defaults.

**UX / design system**: "Observation Deck" direction — a dark-first, telemetry-themed arena where
each provider runs in its own brand-hued lane and streams its answer live with a mono latency
readout; a single warm "ignition" accent for launch/live states. Built with Tailwind CSS +
shadcn/ui (Radix) + Framer Motion over a design-token foundation (color/type/space/radius, light +
dark). Type: Space Grotesk (display), IBM Plex Sans (body), IBM Plex Mono (data/telemetry). The four
screens are mocked in Figma for approval before implementation.

**Quality harness** (Constitution v1.2.0): GitHub Actions CI on every PR — backend `mvn verify`
(JaCoCo 100%-logic gate), frontend Vitest 100%-logic gate + build, and a Docker image build + smoke
test (health check). A backend health endpoint backs the smoke test. The pipeline also runs
**security & quality scanning** (Constitution v1.3.0): Semgrep (SAST/SCA/secrets), SonarCloud
(static-analysis quality), Trivy (image CVEs), and mutation testing (PIT/Stryker). Required status
checks on `develop`/`main`. The harness is built first (Phase 0) before feature work.

**Constraints**: Provider secrets server-side only (FR-018); per-provider isolation — one failure
must not block/delay others (FR-010); data strictly scoped per user (FR-016); SQLite single-writer
posture under concurrent fan-out; CSRF on state-changing POSTs while the SSE GET authenticates via
session cookie only (`EventSource` cannot set headers)

**Scale/Scope**: 4 screens (login, comparison, results, history); 5 providers (≤4 per prompt); ~7
REST endpoints; 3 persisted entities; solo-maintained academic prototype

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
|---|---|---|
| **I. Spec-Driven Development** | Following constitution → specify → plan → tasks → implement; spec approved with 0 clarification markers before this plan. | ✅ Pass |
| **II. Provider Abstraction & Parallel Isolation** | Single `LlmProvider` interface + uniform result DTO; SDK types confined to adapters. Fan-out via virtual-thread `CompletableFuture` with per-call `orTimeout` + `exceptionally` → each provider resolves to its own success/failure, never blocking others. | ✅ Pass |
| **III. Test Discipline for Integrations** | Provider adapters tested with WireMock (success/empty/error/timeout/SSE) and the router with Mockito — all without live keys/network. Auth covered. Contract/router changes update tests in the same change. | ✅ Pass |
| **IV. Security & Secrets Management** | Keys only in server-side env / git-ignored `.env`, never sent to client (FR-018). Spring Security session auth on every protected route; data scoped per user (FR-016); BCrypt password hashing. | ✅ Pass |
| **V. Simplicity & Solo-Maintainability (YAGNI)** | Hybrid abstraction = 3 libraries cover 5 providers (less code). Virtual threads + `CompletableFuture` chosen over preview structured concurrency / reactive stack. SQLite + Docker-local match scope. SSE adds modest complexity but is **required** by FR-009 (independent panel population); aggregate-JSON fallback documented. No speculative abstractions. | ✅ Pass |
| **CI gate** (v1.2.0) | GitHub Actions on every PR: build backend + frontend, run all tests, enforce coverage gate, build the Docker image and smoke-test it (health check). Required status checks on `develop`/`main`. | ✅ Planned (Phase 0) |
| **Coverage gate** (v1.2.0) | 100% line+branch on logic — backend JaCoCo, frontend Vitest — with a documented exclusion list (entities/DTOs, config, entrypoints, generated). Build/PR fails below threshold. | ✅ Planned (Phase 0) |

**Technology & Architecture Constraints**: matches the constitution exactly (React+Vite SPA, Java
Spring Boot REST, SQLite, Docker-local, scope ceiling = minimal auth + ≤4 providers + history). No
deviation.

**Result**: PASS — no violations; Complexity Tracking left empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-prompt-arena-mvp/
├── plan.md              # This file (/speckit-plan output)
├── spec.md              # Feature specification (/speckit-specify)
├── research.md          # Phase 0 output — technical decisions
├── data-model.md        # Phase 1 output — entities
├── quickstart.md        # Phase 1 output — run & validation guide
├── contracts/
│   └── rest-api.md      # Phase 1 output — REST + SSE contract
├── checklists/
│   └── requirements.md  # Spec quality checklist (/speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
backend/                         # Java 21 / Spring Boot REST service
├── src/main/java/.../promptarena/
│   ├── auth/                     # Spring Security config, login/logout, session, BCrypt
│   ├── provider/                 # LlmProvider interface + adapters
│   │   ├── LlmProvider.java
│   │   ├── openai/               # OpenAI SDK adapter (reused for ChatGPT/Grok/DeepSeek base URLs)
│   │   ├── anthropic/            # Anthropic SDK adapter (Claude)
│   │   └── google/               # Google GenAI SDK adapter (Gemini)
│   ├── comparison/               # Fan-out orchestrator, SSE streaming, controllers
│   ├── history/                  # History query endpoints
│   ├── model/                    # JPA entities: User, Comparison, ProviderResult
│   └── config/                   # Executors (virtual threads), SQLite/JPA, CORS/CSRF
├── src/main/resources/
│   └── application.properties    # SQLite datasource, dialect, pool, timeouts
└── src/test/java/...             # JUnit 5 + WireMock (adapters) + Mockito (orchestration)

│   ├── health/                   # Health endpoint (Actuator or /api/health) for the CI smoke test
│   └── ...
└── pom.xml                       # incl. JaCoCo 100%-logic coverage gate + exclusions

frontend/                        # React 18 + Vite (TypeScript) SPA
├── src/
│   ├── pages/                    # Login, Comparison, Results, History
│   ├── components/               # ProviderPanel, ProviderPicker, PromptInput, etc.
│   ├── components/ui/            # shadcn/ui (Radix) primitives
│   ├── styles/ (tokens)          # design tokens — Observation Deck palette/type/motion
│   ├── api/                      # REST client + EventSource (SSE) handling
│   └── auth/                     # Session context / route guards
├── src/__tests__/                # Vitest + React Testing Library + MSW
└── vitest.config.ts              # 100%-logic coverage thresholds + exclusions

.github/workflows/ci.yml          # CI: backend+frontend build/test/coverage gate + docker smoke test
docker-compose.yml                # `docker compose up` — backend + served SPA + SQLite volume
Dockerfile(s)                     # Backend image (multi-stage build incl. SPA)
README.md                         # Welcoming public-repo landing (pitch, stack, quickstart, links)
.env.example                      # Documented provider key + model vars (real .env git-ignored)
design/                           # Figma export/reference for the Observation Deck screens
```

**Structure Decision**: Web application (Option 2) — a `backend/` Spring Boot service and a
`frontend/` React+Vite SPA, composed by Docker. This matches the constitution's stack and the TCC
architecture (layered client-server; SPA + Spring Boot REST API + SQLite, containerized for local
execution). The SPA is served same-origin with the API in the Docker image so the session cookie is
same-origin (research Decision 4).

**Auth/US1 sequencing note**: although Sign-in is its own P1 story (US3), the Spring Security
framework + a seeded user live in the Foundational phase so US1 (the core comparison) can be built
and tested behind an authenticated session before the user-facing login/logout journey (US3) is
implemented. This keeps each story independently testable without circular dependencies.

## Complexity Tracking

> No constitution violations — section intentionally empty.
