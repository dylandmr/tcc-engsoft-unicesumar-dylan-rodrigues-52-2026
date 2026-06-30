---
description: "Task list for Prompt Arena MVP implementation"
---

# Tasks: Prompt Arena MVP

**Input**: Design documents from `specs/001-prompt-arena-mvp/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/rest-api.md, quickstart.md

**Tests**: Included. The constitution (Principle III — Test Discipline for Integrations) MANDATES
automated tests for provider integrations, the fan-out router, and authentication, runnable without
live keys/network. Test tasks below are therefore required, not optional.

**Organization**: Tasks are grouped by user story (from spec.md) to enable independent
implementation and testing. All three P1 stories plus the P2 history story are covered.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1=core comparison, US2=isolated failures, US3=auth, US4=history

## Path Conventions

Web app (per plan.md): backend at `backend/`, frontend at `frontend/`. Backend Java package root:
`backend/src/main/java/com/promptarena/`; backend tests: `backend/src/test/java/com/promptarena/`;
frontend: `frontend/src/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create monorepo structure (`backend/`, `frontend/`, root `docker-compose.yml`, `.env.example`) per plan.md
- [ ] T002 Initialize Spring Boot 3.x backend (Maven, Java 21) in `backend/pom.xml` with web, security, validation, data-jpa starters
- [ ] T003 [P] Add provider + infra dependencies to `backend/pom.xml` (`com.openai:openai-java`, `com.anthropic:anthropic-java`, `com.google.genai:google-genai`, `org.xerial:sqlite-jdbc`, `org.hibernate.orm:hibernate-community-dialects`, WireMock, JUnit 5, Mockito)
- [ ] T004 [P] Initialize React 18 + Vite + TypeScript app in `frontend/` (`package.json`, `vite.config.ts`, Vitest + React Testing Library + MSW dev deps)
- [ ] T005 [P] Configure backend formatting (Spotless) and frontend ESLint + Prettier configs
- [ ] T006 [P] Create `.env.example` documenting provider key vars (OPENAI/ANTHROPIC/GOOGLE/XAI/DEEPSEEK) and confirm `.env`, `data/`, `*.db` are in `.gitignore`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T007 Configure SQLite datasource + JPA in `backend/src/main/resources/application.properties` (jdbc:sqlite url, `org.sqlite.JDBC`, `SQLiteDialect`, HikariCP `maximum-pool-size=1`, `ddl-auto=update`)
- [ ] T008 [P] Create JPA entity `User` (id, unique username, passwordHash, createdAt) in `backend/src/main/java/com/promptarena/model/User.java`
- [ ] T009 [P] Create JPA entity `Comparison` (id, user FK, prompt, `status` PENDING/COMPLETE, createdAt) + `Status` enum in `backend/src/main/java/com/promptarena/model/Comparison.java`
- [ ] T010 [P] Create JPA entity `ProviderResult` + `Provider`/`Outcome` enums (provider, outcome, responseText, errorMessage, responseTimeMs; unique (comparison, provider)) in `backend/src/main/java/com/promptarena/model/`
- [ ] T011 [P] Create Spring Data repositories `UserRepository`, `ComparisonRepository` in `backend/src/main/java/com/promptarena/repository/`
- [ ] T012 Configure Spring Security session auth (BCryptPasswordEncoder, protected routes, CSRF for state-changing requests) in `backend/src/main/java/com/promptarena/config/SecurityConfig.java`
- [ ] T013 Seed a demo user at startup (CommandLineRunner) in `backend/src/main/java/com/promptarena/config/DataSeeder.java`
- [ ] T014 [P] Define uniform `LlmProvider` interface + `PromptRequest`/`ProviderResult` DTOs (no SDK types leak) in `backend/src/main/java/com/promptarena/provider/LlmProvider.java`
- [ ] T015 [P] Configure virtual-thread executor bean in `backend/src/main/java/com/promptarena/config/ConcurrencyConfig.java`
- [ ] T016 [P] Add global REST error handling (`@RestControllerAdvice`) with machine-readable error codes in `backend/src/main/java/com/promptarena/config/ApiExceptionHandler.java`
- [ ] T017 [P] Scaffold frontend routing + protected-route guard + session context in `frontend/src/App.tsx` and `frontend/src/auth/`
- [ ] T018 [P] Create frontend REST client + SSE (`EventSource`) helper in `frontend/src/api/`

**Checkpoint**: Foundation ready — user story implementation can now begin

---

## Phase 3: User Story 1 - Compare a prompt across multiple providers (Priority: P1) 🎯 MVP

**Goal**: Signed-in user submits a prompt to up to 4 providers and sees each response in its own
panel, side by side, populating independently as each provider returns.

**Independent Test**: Submit a prompt with 3–4 providers; confirm one labeled panel per provider and
that each fills as its provider responds (fast before slow).

### Tests for User Story 1 ⚠️ (write first, ensure they fail)

- [ ] T019 [P] [US1] WireMock contract test for OpenAI-compatible adapter (success + empty) in `backend/src/test/java/com/promptarena/provider/openai/OpenAiCompatibleProviderTest.java`
- [ ] T020 [P] [US1] WireMock contract test for Anthropic adapter in `backend/src/test/java/com/promptarena/provider/anthropic/AnthropicProviderTest.java`
- [ ] T021 [P] [US1] WireMock contract test for Google GenAI adapter in `backend/src/test/java/com/promptarena/provider/google/GeminiProviderTest.java`
- [ ] T022 [P] [US1] Orchestrator fan-out test (parallel dispatch, all succeed, results matched to providers) with Mockito in `backend/src/test/java/com/promptarena/comparison/ComparisonServiceTest.java`
- [ ] T023 [P] [US1] Frontend test: results render one panel per provider and fill independently (Vitest + RTL + MSW SSE) in `frontend/src/__tests__/Results.test.tsx`

### Implementation for User Story 1

- [ ] T024 [P] [US1] Implement OpenAI-compatible adapter (reused for ChatGPT/Grok/DeepSeek via base URL) in `backend/src/main/java/com/promptarena/provider/openai/OpenAiCompatibleProvider.java`
- [ ] T025 [P] [US1] Implement Anthropic adapter in `backend/src/main/java/com/promptarena/provider/anthropic/AnthropicProvider.java`
- [ ] T026 [P] [US1] Implement Google GenAI adapter in `backend/src/main/java/com/promptarena/provider/google/GeminiProvider.java`
- [ ] T027 [US1] Implement provider registry/factory mapping `Provider` enum → `LlmProvider`, reading `{base URL, API key, model id}` per provider from env (`*_MODEL` with defaults) in `backend/src/main/java/com/promptarena/provider/ProviderRegistry.java` (depends on T024–T026)
- [ ] T028 [US1] Implement fan-out orchestrator (`CompletableFuture.supplyAsync` over virtual-thread executor, `orTimeout` then `exceptionally`) and capture per-provider `response_time_ms` in `backend/src/main/java/com/promptarena/comparison/ComparisonService.java` (depends on T027)
- [ ] T029 [US1] Implement `POST /api/comparisons` — validate (non-empty prompt ≤ `MAX_PROMPT_LEN`, 1–4 providers, no duplicates, known providers), **persist the comparison as PENDING and return its id without dispatching providers** — in `backend/src/main/java/com/promptarena/comparison/ComparisonController.java`
- [ ] T030 [US1] Implement `GET /api/comparisons/{id}/stream` SSE emitter that **triggers the fan-out on subscription when PENDING, or replays persisted results when COMPLETE**, pushing `result`/`done` events per provider, in `backend/src/main/java/com/promptarena/comparison/ComparisonStreamController.java`
- [ ] T031 [P] [US1] Build `PromptInput` + `ProviderPicker` (enforce max 4, no duplicates) components in `frontend/src/components/`
- [ ] T032 [P] [US1] Build `ProviderPanel` + Results view consuming the SSE stream in `frontend/src/pages/Results.tsx`
- [ ] T033 [US1] Wire Comparison page (submit → create → open SSE → route events to panels) in `frontend/src/pages/Comparison.tsx` (depends on T031, T032)
- [ ] T034 [US1] Add client + server validation messages (empty prompt, no providers, >4, duplicate) surfaced in UI

**Checkpoint**: Core side-by-side comparison works end-to-end behind the seeded session.

---

## Phase 4: User Story 2 - Isolated handling of provider failures (Priority: P1)

**Goal**: A failing/slow/timed-out provider shows its own error state in its panel without blocking
or failing the others.

**Independent Test**: Force one provider to fail/timeout in a comparison with ≥2 healthy providers;
confirm only the failing panel errors and the others render normally; force all to fail and confirm
each panel errors independently.

### Tests for User Story 2 ⚠️ (write first, ensure they fail)

- [ ] T035 [P] [US2] Isolation test: one provider errors, others still succeed (Mockito orchestrator) in `backend/src/test/java/com/promptarena/comparison/IsolationTest.java`
- [ ] T036 [P] [US2] Timeout test: one provider exceeds `PROVIDER_TIMEOUT_MS` → `TIMEOUT`, others unaffected (WireMock fixed delay) in `backend/src/test/java/com/promptarena/comparison/TimeoutTest.java`
- [ ] T037 [P] [US2] All-providers-fail test: each panel gets its own error; request is not a single catastrophic failure in `backend/src/test/java/com/promptarena/comparison/AllFailTest.java`
- [ ] T038 [P] [US2] Frontend test: error, timeout, and empty panel states render distinctly from success in `frontend/src/__tests__/ProviderPanel.test.tsx`

### Implementation for User Story 2

- [ ] T039 [US2] Add per-provider timeout config (`PROVIDER_TIMEOUT_MS`) and apply `orTimeout` per future in `backend/src/main/java/com/promptarena/comparison/ComparisonService.java`
- [ ] T040 [US2] Implement outcome classification (SUCCESS/EMPTY/ERROR/TIMEOUT) + exception→error-code mapping in `backend/src/main/java/com/promptarena/provider/ProviderResultMapper.java`
- [ ] T041 [P] [US2] Render distinct panel states (error, timeout, empty vs success) in `frontend/src/components/ProviderPanel.tsx`
- [ ] T042 [US2] Ensure SSE emits each provider's `result`/error event without aborting the stream on any provider failure in `backend/src/main/java/com/promptarena/comparison/ComparisonStreamController.java`

**Checkpoint**: Partial failures are isolated and visible; core resilience requirement met.

---

## Phase 5: User Story 3 - Account access via login and logout (Priority: P1)

**Goal**: Users sign in with username/password to reach the app and sign out; protected routes are
gated and data is scoped per user.

**Independent Test**: Access comparison/history while signed out → denied/redirected; sign in with
valid creds → access granted; invalid creds → non-revealing error; sign out → access revoked.

### Tests for User Story 3 ⚠️ (write first, ensure they fail)

- [ ] T043 [P] [US3] Auth test: protected endpoints return 401 when unauthenticated in `backend/src/test/java/com/promptarena/auth/ProtectedRouteTest.java`
- [ ] T044 [P] [US3] Auth test: invalid credentials → non-revealing error; valid login establishes session; logout invalidates in `backend/src/test/java/com/promptarena/auth/AuthFlowTest.java`
- [ ] T045 [P] [US3] Frontend test: signed-out access redirects to login; login + logout flow works in `frontend/src/__tests__/Login.test.tsx`

### Implementation for User Story 3

- [ ] T046 [US3] Implement `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` in `backend/src/main/java/com/promptarena/auth/AuthController.java`
- [ ] T047 [US3] Implement `UserDetailsService` + BCrypt authentication wiring in `backend/src/main/java/com/promptarena/auth/AppUserDetailsService.java`
- [ ] T048 [P] [US3] Build Login page + form validation in `frontend/src/pages/Login.tsx`
- [ ] T049 [US3] Wire route guards + session bootstrap (`GET /auth/me`) + logout control in `frontend/src/auth/` and app shell

**Checkpoint**: Full auth journey works; protected screens enforce sign-in.

---

## Phase 6: User Story 4 - Review past comparisons in history (Priority: P2)

**Goal**: A signed-in user reviews their past comparisons (prompt, providers, responses), scoped to
their own account, with an empty state when none exist.

**Independent Test**: Run comparisons, open History → own prompts listed newest-first with results;
a second user sees an empty state and never the first user's data.

### Tests for User Story 4 ⚠️ (write first, ensure they fail)

- [ ] T050 [P] [US4] History scoping test: a user sees only their own comparisons (0 cross-user leakage) in `backend/src/test/java/com/promptarena/history/HistoryScopingTest.java`
- [ ] T051 [P] [US4] Persistence test: a completed comparison is saved including failed-provider outcomes in `backend/src/test/java/com/promptarena/history/PersistenceTest.java`
- [ ] T052 [P] [US4] Frontend test: history list and empty state render correctly in `frontend/src/__tests__/History.test.tsx`

### Implementation for User Story 4

- [ ] T053 [US4] Persist `Comparison` + `ProviderResult`s after fan-out completes via a single writer path and set `status = COMPLETE` in `backend/src/main/java/com/promptarena/comparison/ComparisonService.java`
- [ ] T054 [US4] Implement `GET /api/comparisons` (list, newest-first, per-user) and `GET /api/comparisons/{id}` (detail; 404 if not owned) in `backend/src/main/java/com/promptarena/history/HistoryController.java`
- [ ] T055 [P] [US4] Build History list (with empty state) + detail view (showing each provider's response and `response_time_ms`) in `frontend/src/pages/History.tsx`
- [ ] T056 [US4] Surface per-provider `response_time_ms` (captured in T028) in the comparison detail/history response and view (constitution/TCC requirement)

**Checkpoint**: All user stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Packaging, hardening, and end-to-end validation across stories

- [ ] T057 [P] Multi-stage `backend/Dockerfile` (build SPA + backend; serve SPA same-origin with the API)
- [ ] T058 Author root `docker-compose.yml` so `docker compose up` runs backend + persists SQLite volume; wire env vars
- [ ] T059 [P] Enable `PRAGMA journal_mode=WAL` + `busy_timeout` at startup and verify no `SQLITE_BUSY` under concurrent writes
- [ ] T060 [P] Security hardening: confirm CSRF on state-changing routes and that provider keys never reach the client (FR-018)
- [ ] T061 [P] Write `README.md` run instructions and complete `.env.example`
- [ ] T062 Run `quickstart.md` scenarios V1–V4 end-to-end and record results
- [ ] T063 [P] Enforce `MAX_PROMPT_LEN` and handle long-response panel display (scroll / expand)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: depends on Setup — BLOCKS all user stories.
- **User Stories (Phases 3–6)**: all depend on Foundational. US1 is the MVP and is built first; US2
  extends US1's fan-out (depends on US1 core); US3 (auth journey) and US4 (history) depend on
  Foundational and can largely proceed in parallel with each other after US1.
- **Polish (Phase 7)**: depends on the targeted stories being complete.

### User Story Dependencies

- **US1 (P1)**: after Foundational. The core.
- **US2 (P1)**: builds on US1's orchestrator/SSE (timeout + outcome states). Start after US1 core.
- **US3 (P1)**: after Foundational (Security framework is in Foundational; this adds the user-facing
  login/logout journey). Independent of US1/US2.
- **US4 (P2)**: after Foundational; persists results produced by US1's orchestrator, so best after
  US1. Independent of US2/US3.

### Within Each User Story

- Tests written first and FAIL before implementation (Constitution III).
- Models → repositories → services → endpoints → UI.
- Commit after each task or logical group (per Gitflow on `feature/001-prompt-arena-mvp`).

### Parallel Opportunities

- Setup: T003, T004, T005, T006 in parallel.
- Foundational: T008, T009, T010, T011 (entities/repos), and T014–T018 in parallel after T007.
- US1: the three adapter tests (T019–T021) and adapters (T024–T026) in parallel; frontend T031/T032
  in parallel with backend.
- Tests marked [P] within a story run in parallel.

---

## Parallel Example: User Story 1

```text
# Adapter contract tests together:
Task: T019 WireMock test for OpenAI-compatible adapter
Task: T020 WireMock test for Anthropic adapter
Task: T021 WireMock test for Google GenAI adapter

# Adapter implementations together:
Task: T024 OpenAI-compatible adapter
Task: T025 Anthropic adapter
Task: T026 Google GenAI adapter
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → **STOP & VALIDATE** (V1) → demo.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 → validate (V1) → MVP demo.
3. US2 → validate (V2) → resilience demo.
4. US3 → validate (V3) → full auth journey.
5. US4 → validate (V4) → history.
6. Polish → `docker compose up` + full quickstart pass.

---

## Notes

- [P] = different files, no incomplete-task dependency.
- [Story] label maps each task to a spec user story for traceability (and to GitHub issues).
- Tests precede implementation within each story; verify they fail first (Constitution III).
- Commit after each task or logical group; keep GitHub issues in sync with this file over time.
