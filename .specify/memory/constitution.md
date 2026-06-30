<!--
Sync Impact Report
==================
Version change: 1.2.0 → 1.3.0
Rationale: Expanded the Continuous Integration gate to require automated security & quality
scanning in the pipeline — dependency/SAST/secret scanning (Semgrep), static-analysis quality
(SonarCloud), container image vulnerability scanning (Trivy), and a mutation-testing check
(PIT/Stryker). Materially expanded gate, no core principle redefined → MINOR bump.

----- Prior change (1.1.0 → 1.2.0) -----
Added two non-negotiable quality gates to "Development Workflow & Quality Gates" — a
Continuous Integration gate and a Test Coverage gate — and strengthened the Definition of Done
accordingly.

Sections modified (1.2.0):
  ~ Development Workflow & Quality Gates
      + CI gate: every PR MUST pass an automated pipeline (build, tests, coverage gate, container
        image build + smoke test) before merge; gates are required status checks on protected branches.
      + Coverage gate: logic code MUST hold 100% line+branch coverage (backend JaCoCo, frontend
        Vitest) with a documented, reviewed exclusion list for non-logic (entities/DTOs, config,
        entrypoints, generated code); the build/PR fails below the threshold.
  ~ Definition of Done: now requires the CI pipeline green (incl. coverage gate) and a successful
        containerized run.

----- Prior change (1.0.0 → 1.1.0) -----
Materially revised two sections to match the authoritative TCC document
(docs/DYLAN RODRIGUES - ATIVIDADE 1 ... 52_2026.pdf). No core principle was added, removed, or
redefined.

Principles (unchanged):
  I.   Spec-Driven Development
  II.  Provider Abstraction & Parallel Isolation
  III. Test Discipline for Integrations
  IV.  Security & Secrets Management
  V.   Simplicity & Solo-Maintainability (YAGNI)

Sections modified:
  ~ Technology & Architecture Constraints
      - Frontend tooling corrected: "React with Node.js/Express tooling" → React SPA bundled with Vite
      - Persistence corrected: NoSQL document DB (MongoDB/DocumentDB) → SQLite (embedded relational)
      - Deployment corrected: cloud (AWS/GCP) + public domain → Docker for reproducible LOCAL
        execution (`docker compose up`); source/specs in a public GitHub repository
      - Backend clarified: Spring Boot REST API records each provider's response time
  ~ Development Workflow & Quality Gates
      - Methodology corrected: Scrum/monthly sprints + Trello Kanban → agile, iterative agentic
        coding with Claude Code, organized via Spec-Driven Development (GitHub Spec Kit)
      - Validation corrected: peer review/System Demo with family/friends → incremental internal
        review via direct access at the end of each specification/implementation cycle

Templates reviewed for alignment:
  ✅ .specify/templates/plan-template.md  (Constitution Check gate compatible; no stack hardcoded)
  ✅ .specify/templates/spec-template.md  (no mandatory section changes required)
  ✅ .specify/templates/tasks-template.md (principle-driven task types still covered)

Follow-up TODOs: none. RATIFICATION_DATE unchanged (2026-06-28); LAST_AMENDED_DATE → 2026-06-29.
-->

# Prompt Arena Constitution

Prompt Arena is a web platform for the parallel, comparative evaluation of generative AI
providers. A single user query is dispatched concurrently to multiple LLM providers (Google
Gemini, OpenAI ChatGPT, Anthropic Claude, xAI Grok, DeepSeek) and their responses are presented
side by side so that biases and quality differences can be analyzed. This constitution defines the
non-negotiable principles that govern how the software is specified, built, and maintained.

## Core Principles

### I. Spec-Driven Development
Every feature MUST begin as a written specification before implementation. The flow is
`/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`; code is never
written ahead of an approved spec and plan. Specifications MUST describe observable behavior and
acceptance criteria, not implementation detail. Rationale: as a solo academic project, the spec is
the durable record of intent that the thesis and peer reviews are evaluated against.

### II. Provider Abstraction & Parallel Isolation
Every LLM provider MUST be accessed through a single uniform interface; provider-specific SDK
details MUST NOT leak into routing, UI, or persistence layers. A query fans out to up to four
providers concurrently, and each provider call MUST be isolated: one provider's failure, timeout,
or slow response MUST NOT block, fail, or delay the others. Per-provider errors are surfaced as
that provider's result, never as a whole-request failure. Rationale: fair side-by-side comparison
and resilience are the core value of the product.

### III. Test Discipline for Integrations
Provider integrations, the fan-out router, and authentication MUST be covered by automated tests.
Provider calls MUST be tested against mocked/stubbed responses so the suite runs without live API
keys or network access. Any change to a provider contract or to the routing logic MUST add or
update tests in the same change. Rationale: external LLM APIs are the highest-risk, most volatile
dependency; tests are the only guard against silent regressions.

### IV. Security & Secrets Management
Provider API keys and credentials MUST NEVER be committed to the repository or exposed to the
frontend; they live only in environment configuration / secret stores and are used server-side.
Session authentication (login/logout) MUST be enforced on every protected route and history record,
scoped per user. Secrets MUST be excluded via `.gitignore` and rotated if leaked. Rationale: leaked
LLM keys are a direct financial and reputational liability, and user history is private data.

### V. Simplicity & Solo-Maintainability (YAGNI)
The simplest solution that satisfies the spec MUST be preferred. Managed/hosted services are
favored over self-built infrastructure, and abstractions are introduced only when a second concrete
use exists — not in anticipation. Scope additions beyond the documented product scope MUST be
justified against the TCC deadline. Rationale: a single maintainer on an academic timeline cannot
service accidental complexity.

## Technology & Architecture Constraints

The approved stack is authoritative; deviations require a documented justification in the relevant
plan's Constitution Check.

- **Frontend**: React, built as a Single Page Application (SPA) and bundled with Vite, serving the
  single-page comparison interface.
- **Backend**: Java Spring Boot as the core routing engine, exposing a REST API that centralizes
  session control, integrates the provider SDKs, dispatches the prompt to providers in parallel and
  isolated, and records each provider's response time.
- **Persistence**: SQLite, a lightweight embedded relational database, for authentication data and
  prompt/response history; suited to the local-prototype nature of the project.
- **Deployment**: Containerized with Docker for a reproducible local execution environment,
  launchable with a single command (`docker compose up`). This is a local prototype, not a
  cloud-hosted service; source code and the Spec-Kit specifications are kept in a public GitHub
  repository.
- **Scope ceiling**: minimal user/password authentication, parallel execution of up to four
  providers per prompt, and a history view. Anything beyond this is out of scope unless added to the
  product specification first.

## Development Workflow & Quality Gates

- **Methodology**: an agile, iterative approach centered on agentic coding, in which the developer
  collaborates with AI agents (Claude Code by Anthropic) to specify, plan, implement, and review the
  software, organized via Spec-Driven Development supported by GitHub Spec Kit (constitution →
  specify → plan → tasks → implement). No code is written before a reviewed and approved spec.
- **Spec-Kit gates**: a feature is "ready to implement" only after `/speckit-plan` and
  `/speckit-tasks`; `/speckit-analyze` SHOULD be run before `/speckit-implement` for non-trivial
  features.
- **Constitution Check**: every plan MUST pass the Constitution Check section; any violation MUST be
  recorded with explicit justification or the design MUST be revised.
- **Continuous Integration gate**: every pull request MUST pass an automated CI pipeline before
  merge — at minimum: build the backend and frontend, run all automated tests, enforce the coverage
  gate (below), and build the Docker image and smoke-test it (container starts and a health check
  returns success). The pipeline MUST additionally run automated **security & quality scanning** —
  dependency/SAST/secret scanning (Semgrep), static-analysis quality (SonarCloud), container image
  vulnerability scanning (Trivy), and a mutation-testing check (PIT for Java, Stryker for the
  frontend). These checks MUST be configured as required status checks on protected branches
  (`develop`, `main`); a failing pipeline blocks the merge.
- **Coverage gate**: logic code MUST maintain 100% line and branch coverage — backend via JaCoCo,
  frontend via Vitest — enforced in CI so the build/PR fails below the threshold. A documented,
  reviewed exclusion list MAY exempt genuinely non-logic code (JPA entities/DTOs, framework
  configuration, application entrypoints, generated code); exclusions are deliberate, not a means of
  evading meaningful tests.
- **Validation**: performed incrementally via direct access to the running local prototype and
  internal reviews at the end of each specification/implementation cycle.
- **Definition of done**: spec satisfied; the CI pipeline green (build, all tests, 100% coverage
  gate, and a successful containerized run); secrets kept out of version control; and behavior
  validated through direct access and internal review.

## Governance

This constitution supersedes ad-hoc practices for the Prompt Arena project. Amendments MUST be made
by editing this file via the `/speckit-constitution` workflow, which also re-validates dependent
templates. Versioning follows semantic rules: **MAJOR** for backward-incompatible principle
removals or redefinitions, **MINOR** for a new principle or materially expanded section, **PATCH**
for clarifications and wording. All plans and reviews MUST verify compliance with these principles,
and unjustified complexity is grounds for rejecting a change. Runtime development guidance for the
coding agent lives in `CLAUDE.md`.

**Version**: 1.3.0 | **Ratified**: 2026-06-28 | **Last Amended**: 2026-06-30
