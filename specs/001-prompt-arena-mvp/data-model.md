# Data Model: Prompt Arena MVP

**Feature**: 001-prompt-arena-mvp
**Date**: 2026-06-29
**Source**: Derived from `spec.md` (Key Entities, Functional Requirements) and the constitution
(SQLite embedded relational persistence).

## Overview

Four core concepts persist in a single embedded SQLite database: **User**, **Comparison**, and
**ProviderResult**. **Provider** is a fixed enumeration (not a table) since the supported set is
closed at five. Relationships:

```text
User (1) ──< (N) Comparison (1) ──< (N) ProviderResult
                                         provider: enum(GEMINI, CHATGPT, CLAUDE, GROK, DEEPSEEK)
```

A `User` owns many `Comparison`s. A `Comparison` has one `ProviderResult` per selected provider
(between 1 and 4). `Provider` identity is stored on `ProviderResult` as an enum value.

## Entities

### User

Represents an authenticated person. Owns all their comparisons; data is strictly scoped per user
(FR-016).

| Field           | Type            | Constraints                                              |
|-----------------|-----------------|----------------------------------------------------------|
| id              | UUID / string   | Primary key                                              |
| username        | string          | Unique, not null, case-insensitive match                 |
| password_hash   | string          | Not null; salted hash (never plaintext) — see research   |
| created_at      | timestamp       | Not null, default now                                    |

**Rules**:
- `username` MUST be unique (FR-002).
- `password_hash` MUST be a one-way salted hash; the plaintext password is never stored or logged
  (Constitution IV — Security).
- Accounts are provisioned out of band for the MVP (spec Assumptions); no self-service registration
  endpoint is required.

### Comparison

A single submitted prompt and the set of providers it targeted (FR-014).

| Field        | Type          | Constraints                                                  |
|--------------|---------------|--------------------------------------------------------------|
| id           | UUID / string | Primary key                                                  |
| user_id      | FK → User.id  | Not null; indexed (history queries filter by user)           |
| prompt       | text          | Not null, non-empty (FR-006)                                 |
| status       | enum (Status) | Not null: PENDING (created, not yet run) or COMPLETE (run+persisted) |
| created_at   | timestamp     | Not null, default now; history is ordered by this descending |

A `Comparison` also carries one **selected model per provider** (element collection
`comparison_models`: `comparison_id`, `provider`, `model` — FR-020). The model is the user's
explicit choice, required for every selected provider and validated at `POST` time against the
models that provider's API reports; it is the model the lazy fan-out requests from that provider.
There are no default models. Comparisons persisted before model selection existed have no rows
here; if such a comparison is still `PENDING` when its stream opens, each provider without a
recorded model yields its own `ERROR` result (nothing valid to request) without being called.

**Rules**:
- `prompt` MUST be non-empty (FR-006). A maximum length is enforced (see research / quickstart).
- A `Comparison` MUST reference between 1 and 4 providers via its `ProviderResult` children (FR-005).
- Each selected provider has exactly one entry in `comparison_models` (for comparisons created
  after model selection existed), and its value MUST have been in that provider's API-reported
  model set when the comparison was created (FR-020).
- Only **completed** comparisons are guaranteed to be persisted (spec Assumptions: in-flight on
  logout not guaranteed).
- A `Comparison` is only ever readable by its owning `user_id` (FR-016).
- `status` starts `PENDING` on `POST`; opening the SSE stream runs the fan-out and, on completion,
  persists results and sets `status = COMPLETE`. A re-opened stream for a `COMPLETE` comparison
  replays persisted results (no provider re-call).

### ProviderResult

One provider's outcome for a comparison (FR-011, FR-013, FR-014).

| Field            | Type                | Constraints                                            |
|------------------|---------------------|--------------------------------------------------------|
| id               | UUID / string       | Primary key                                            |
| comparison_id    | FK → Comparison.id  | Not null; indexed                                      |
| provider         | enum (Provider)     | Not null; unique per comparison (no duplicate provider)|
| outcome          | enum (Outcome)      | Not null: SUCCESS, EMPTY, ERROR, TIMEOUT               |
| response_text    | text                | Nullable (null/empty for non-SUCCESS or EMPTY)         |
| error_message    | string              | Nullable; populated for ERROR/TIMEOUT outcomes         |
| response_time_ms | integer             | Nullable; latency recorded per provider (TCC doc)      |
| first_token_ms   | integer             | Nullable; ms until first streamed token, same clock as response_time_ms (FR-019) |
| input_tokens     | integer             | Nullable; provider-reported prompt token count (FR-019)|
| output_tokens    | integer             | Nullable; provider-reported completion token count (FR-019) |
| model            | string              | Nullable; exact model id the provider reports answered (FR-019) |

**Rules**:
- `(comparison_id, provider)` MUST be unique — a provider appears at most once per comparison
  (FR-005, edge case: no duplicate selection).
- `outcome` distinguishes a successful-but-empty response (`EMPTY`) from an error (`ERROR`) and a
  timeout (`TIMEOUT`) (FR-013, FR-012).
- For `SUCCESS`, `response_text` is present; for `EMPTY`, `response_text` is empty and `outcome` is
  still success-class; for `ERROR`/`TIMEOUT`, `error_message` explains the failure (FR-011).
- `response_time_ms` records the wall-clock latency of that provider's call when measurable
  (constitution: backend records each provider's response time).
- `first_token_ms`, `input_tokens`, `output_tokens` and `model` record per-result telemetry when the
  provider reports it (FR-019): time-to-first-token (measured inside the adapter on the same clock
  epoch as `response_time_ms`, so derived rates like tokens/s are consistent), the provider's own
  token usage, and the exact model identifier. All four are null when unavailable — e.g. `TIMEOUT`
  results (the adapter never returned) and `ERROR` results carry no telemetry.

## Enumerations

### Provider

Closed set of supported providers (FR-005). Stored as a string enum.

```text
GEMINI    — Google Gemini
CHATGPT   — OpenAI ChatGPT
CLAUDE    — Anthropic Claude
GROK      — xAI Grok
DEEPSEEK  — DeepSeek
```

Adding a sixth provider is out of scope unless the spec is amended (spec Assumptions).

### Outcome

```text
SUCCESS   — provider returned non-empty content
EMPTY     — provider returned successfully but with no content
ERROR     — provider call failed (HTTP error, SDK exception, invalid response)
TIMEOUT   — provider exceeded the per-provider response time limit (FR-012)
```

### Status (Comparison lifecycle)

```text
PENDING   — created and validated on POST; fan-out not yet run
COMPLETE  — fan-out finished and ProviderResults persisted
```

## Validation Summary (traceability to FRs)

| Rule                                              | Requirement |
|---------------------------------------------------|-------------|
| Unique username                                   | FR-002      |
| Password stored only as salted hash, server-side  | FR-018, Constitution IV |
| Prompt non-empty                                  | FR-006      |
| 1–4 providers per comparison, no duplicates       | FR-005      |
| Per-provider outcome isolated and recorded        | FR-010, FR-011, FR-014 |
| EMPTY distinct from ERROR                          | FR-013      |
| TIMEOUT recorded after per-provider limit          | FR-012      |
| History scoped to owning user                     | FR-015, FR-016 |
| Per-provider response time captured               | Constitution / TCC |
| Per-provider telemetry (TTFT, token usage, model) captured when reported | FR-019 |
| Model per provider explicitly chosen on POST, validated against the provider-reported set, persisted | FR-020 |
