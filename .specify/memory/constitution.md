<!--
Sync Impact Report
==================
Version change: (template) → 1.0.0
Rationale: Initial ratification of the Prompt Arena constitution (MINOR/MAJOR n/a; first adoption → 1.0.0).

Principles defined:
  I.   Spec-Driven Development
  II.  Provider Abstraction & Parallel Isolation
  III. Test Discipline for Integrations
  IV.  Security & Secrets Management
  V.   Simplicity & Solo-Maintainability (YAGNI)

Sections:
  Added: Technology & Architecture Constraints
  Added: Development Workflow & Quality Gates
  Added: Governance

Templates reviewed for alignment:
  ✅ .specify/templates/plan-template.md  (Constitution Check gate compatible)
  ✅ .specify/templates/spec-template.md  (no mandatory section changes required)
  ✅ .specify/templates/tasks-template.md (principle-driven task types covered)

Follow-up TODOs: none. RATIFICATION_DATE set to first adoption date (2026-06-28).
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

- **Frontend**: React with Node.js/Express tooling, served as a single-page comparison interface.
- **Backend**: Java Spring Boot as the core routing engine, integrating provider SDKs.
- **Persistence**: NoSQL document database (MongoDB or Amazon DocumentDB) for authentication data
  and prompt/response history.
- **Deployment**: Containerized for cloud hosting (AWS or GCP) and published to a public domain.
- **Scope ceiling**: minimal user/password authentication, parallel execution of up to four
  providers per prompt, and a history view. Anything beyond this is out of scope unless added to the
  product specification first.

## Development Workflow & Quality Gates

- **Methodology**: Agile via Scrum with monthly sprints; Kanban tracking on Trello. Validation is
  performed through peer review/System Demo with family, friends, or colleagues.
- **Spec-Kit gates**: a feature is "ready to implement" only after `/speckit-plan` and
  `/speckit-tasks`; `/speckit-analyze` SHOULD be run before `/speckit-implement` for non-trivial
  features.
- **Constitution Check**: every plan MUST pass the Constitution Check section; any violation MUST be
  recorded with explicit justification or the design MUST be revised.
- **Definition of done**: spec satisfied, automated tests for affected integrations passing, secrets
  kept out of version control, and behavior validated in a peer review or demo.

## Governance

This constitution supersedes ad-hoc practices for the Prompt Arena project. Amendments MUST be made
by editing this file via the `/speckit-constitution` workflow, which also re-validates dependent
templates. Versioning follows semantic rules: **MAJOR** for backward-incompatible principle
removals or redefinitions, **MINOR** for a new principle or materially expanded section, **PATCH**
for clarifications and wording. All plans and reviews MUST verify compliance with these principles,
and unjustified complexity is grounds for rejecting a change. Runtime development guidance for the
coding agent lives in `CLAUDE.md`.

**Version**: 1.0.0 | **Ratified**: 2026-06-28 | **Last Amended**: 2026-06-28
