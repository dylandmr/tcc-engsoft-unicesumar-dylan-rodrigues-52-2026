# Feature Specification: Prompt Arena MVP

**Feature Branch**: `001-prompt-arena-mvp`

**Created**: 2026-06-29

**Status**: Draft

**Input**: User description: "Prompt Arena MVP: a web platform for parallel, comparative evaluation of generative AI providers. A signed-in user submits a single prompt that is dispatched concurrently to up to four selected LLM providers (from Google Gemini, OpenAI ChatGPT, Anthropic Claude, xAI Grok, DeepSeek). Each provider's response is rendered side by side as it arrives, so biases and quality differences can be compared. Per-provider failures, timeouts, or slow responses are isolated and surfaced as that provider's own result without blocking the others. The platform includes minimal username/password authentication (login/logout) and a per-user history view of past prompts and their responses. Scope ceiling per the constitution: minimal auth, parallel execution of up to four providers per prompt, and a history view."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Compare a prompt across multiple providers (Priority: P1)

A signed-in user types a single prompt, selects up to four AI providers, and submits. Each
provider's response appears in its own panel side by side, populating independently as each
provider returns, so the user can directly compare wording, accuracy, tone, and bias across
providers for the same input.

**Why this priority**: This is the core value proposition of Prompt Arena — the parallel,
side-by-side comparison is the reason the product exists. Without it there is no product.

**Independent Test**: Sign in, enter a prompt, select two-to-four providers, submit, and confirm
that a distinct response panel appears for each selected provider and that each panel fills in as
its provider responds, independently of the others.

**Acceptance Scenarios**:

1. **Given** a signed-in user on the comparison screen, **When** they enter a prompt, select three
   providers, and submit, **Then** three response panels are shown side by side, each labeled with
   its provider, and each fills with that provider's response as it arrives.
2. **Given** a submitted comparison where one provider responds quickly and another slowly, **When**
   the fast provider returns first, **Then** its panel displays its response immediately without
   waiting for the slower provider.
3. **Given** a signed-in user, **When** they attempt to submit with no provider selected or an empty
   prompt, **Then** submission is blocked and a clear validation message explains what is required.
4. **Given** a signed-in user, **When** they attempt to select a fifth provider, **Then** selection
   beyond four is prevented and the limit is communicated.
5. **Given** a signed-in user who has selected a provider, **When** they open that provider's model
   selector, **Then** they choose which model answers the comparison from exactly the models that
   provider's own API reports as available; **and When** any selected provider has no chosen model,
   **Then** submission is blocked with a clear validation message.

---

### User Story 2 - Isolated handling of provider failures (Priority: P1)

When a comparison runs, any provider that fails, times out, or returns an error has that outcome
shown only in its own panel as an error state. The other providers' panels are unaffected and still
display their successful responses.

**Why this priority**: Resilience and fairness are constitutional requirements (Provider
Abstraction & Parallel Isolation). A comparison that collapses when one provider misbehaves would
be unusable, since external LLM APIs fail routinely.

**Independent Test**: Run a comparison in which at least one selected provider is made to fail or
time out, and confirm the failing provider's panel shows an error while every other panel shows its
normal response.

**Acceptance Scenarios**:

1. **Given** a comparison with four providers where one returns an error, **When** results render,
   **Then** the failing provider's panel shows a clear error message and the other three show their
   responses normally.
2. **Given** a comparison where one provider exceeds the response time limit, **When** the limit is
   reached, **Then** that provider's panel shows a timeout state while the others are unaffected.
3. **Given** a comparison where every selected provider fails, **When** results render, **Then** each
   panel independently shows its own error and the overall request is not reported as a single
   catastrophic failure.

---

### User Story 3 - Account access via login and logout (Priority: P1)

A user signs in with a username and password to reach the comparison tool, and signs out when
finished. Only signed-in users can run comparisons or view history, and each user only ever sees
their own data.

**Why this priority**: Authentication gates the entire product and scopes history per user, which
is a constitutional security requirement. Comparison and history both depend on knowing who the
user is.

**Independent Test**: Attempt to reach the comparison and history screens while signed out and
confirm access is denied; sign in with valid credentials and confirm access is granted; sign out
and confirm access is revoked again.

**Acceptance Scenarios**:

1. **Given** a registered user, **When** they submit valid credentials, **Then** they are signed in
   and taken to the comparison screen.
2. **Given** a signed-out visitor, **When** they try to open the comparison or history screen
   directly, **Then** they are redirected to sign in and cannot access the protected content.
3. **Given** a user who submits invalid credentials, **When** they attempt to sign in, **Then**
   access is denied with a clear, non-revealing error message.
4. **Given** a signed-in user, **When** they sign out, **Then** their session ends and protected
   screens are no longer accessible without signing in again.

---

### User Story 4 - Review past comparisons in history (Priority: P2)

A signed-in user opens a history view listing their previous prompts together with the providers
used and the responses received, so they can revisit earlier comparisons without re-running them.

**Why this priority**: History adds durable value and supports the thesis's analytical goals, but
the product still delivers its core comparison value without it; therefore it ranks below the P1
slices.

**Independent Test**: Run one or more comparisons, open the history view, and confirm those
comparisons appear with their prompt, providers, and responses, and that another user's comparisons
never appear.

**Acceptance Scenarios**:

1. **Given** a signed-in user who has run comparisons, **When** they open history, **Then** they see
   a list of their past prompts with the providers used and the responses captured for each.
2. **Given** a signed-in user with no prior comparisons, **When** they open history, **Then** they
   see a clear empty state rather than an error.
3. **Given** two different users, **When** each opens history, **Then** each sees only their own
   comparisons and never the other's.
4. **Given** a completed comparison in which some providers failed, **When** it is saved to history,
   **Then** the recorded entry reflects both the successful responses and the failed providers'
   outcomes.

---

### Edge Cases

- What happens when the same provider is somehow selected twice? Selection MUST treat each provider
  as a unique, single choice and prevent duplicates.
- How does the system handle a very long prompt or a very long provider response? Input limits and
  display behavior (scroll/truncate-with-expand) MUST be defined so the layout remains usable.
- What happens if the user navigates away or signs out while a comparison is still in flight? In-
  flight results MUST NOT leak to another user, and the partially completed comparison MUST be
  handled predictably (saved as-is or discarded — see Assumptions).
- How does the system behave if a provider returns an empty but successful response? The panel MUST
  distinguish "empty response" from "error".
- What happens when a provider is temporarily unavailable for all users (e.g., outage)? Its panel
  MUST show an error for that comparison without affecting others.
- How are concurrent submissions from the same user handled? Behavior MUST be defined so results are
  never mismatched to the wrong prompt.
- What happens when a provider's model-list API is unreachable or the provider is not configured?
  That provider offers no models and cannot be selected for a comparison (there is nothing valid to
  run) — its card states why — while every other provider remains fully usable (mirrors the
  per-provider isolation principle).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST require authentication before allowing access to the comparison tool or
  history; unauthenticated visitors MUST be denied access to protected screens.
- **FR-002**: System MUST allow a registered user to sign in with a username and password and to
  sign out, ending their session.
- **FR-003**: System MUST reject invalid credentials with a clear message that does not reveal
  whether the username or the password was incorrect.
- **FR-004**: System MUST let a signed-in user enter a single free-text prompt for a comparison.
- **FR-005**: System MUST let the user select between one and four providers, inclusive, from the
  supported set (Google Gemini, OpenAI ChatGPT, Anthropic Claude, xAI Grok, DeepSeek) and MUST
  prevent selecting more than four or selecting duplicates.
- **FR-006**: System MUST prevent submission when the prompt is empty or no provider is selected,
  showing a clear validation message.
- **FR-007**: System MUST dispatch the prompt to all selected providers concurrently, sending the
  same prompt to each.
- **FR-008**: System MUST display one clearly labeled response panel per selected provider, arranged
  for side-by-side comparison. After all selected providers have reported, the results view MUST
  present a comparative summary of the recorded telemetry (FR-019): a ranking by response time,
  with each provider's first-token latency, token counts, and model identity.
- **FR-009**: System MUST populate each provider's panel independently as that provider responds,
  without waiting for slower providers.
- **FR-010**: System MUST isolate per-provider failures, timeouts, and errors so that one provider's
  failure does not block, fail, or delay any other provider's result.
- **FR-011**: System MUST present a provider's failure, timeout, or error as that provider's own
  result state within its panel, and MUST NOT report the whole comparison as failed when at least
  one provider could succeed.
- **FR-012**: System MUST enforce a per-provider response time limit, after which that provider's
  result is shown as a timeout.
- **FR-013**: System MUST distinguish a successful-but-empty provider response from an error
  response in the panel.
- **FR-014**: System MUST record each completed comparison for the submitting user, capturing the
  prompt, the providers used, and each provider's response or failure outcome.
- **FR-015**: System MUST provide a history view that lists the signed-in user's past comparisons
  with their prompt, providers, and responses.
- **FR-016**: System MUST scope all history and comparison data per user, so a user can never see
  another user's prompts or responses.
- **FR-017**: System MUST show a clear empty state in history when the user has no past comparisons.
- **FR-018**: System MUST keep provider credentials server-side only and MUST NOT expose them to the
  user-facing client.
- **FR-019**: System MUST record, per provider result, the telemetry needed for the thesis's
  comparative analysis: the total response time, the time until the first streamed token
  (time-to-first-token, measured on the same clock as the response time), the provider-reported
  input and output token counts, and the exact model identifier the provider reports as having
  answered. Each telemetry value is captured when the provider makes it available and recorded as
  absent (null) otherwise — e.g. a timed-out provider has no telemetry.
- **FR-020**: System MUST require the user to choose, per selected provider, which of that
  provider's models answers the comparison. The choices offered per provider MUST be exactly the
  models that provider's own API reports as available (fetched live for configured providers and
  cached briefly); the system MUST NOT define default, curated, or otherwise hardcoded model
  choices. A comparison MUST NOT be submittable until every selected provider has an explicitly
  chosen model, and a provider whose model list cannot be retrieved (unconfigured, or list-fetch
  failure) MUST NOT be selectable — shown as unavailable without affecting the other providers.
  The chosen model per provider MUST be recorded with the comparison, and a submission missing a
  model or naming one outside the offered set MUST be rejected with a validation error.

### Key Entities *(include if feature involves data)*

- **User**: A person who can sign in to use the platform. Key attributes: a unique username and a
  securely stored password credential. Owns their comparisons and history.
- **Provider**: A supported generative-AI service that can answer a prompt (Google Gemini, OpenAI
  ChatGPT, Anthropic Claude, xAI Grok, DeepSeek). Key attributes: identity/label and availability
  for selection. Accessed through a uniform interface.
- **Comparison**: A single submitted prompt and the set of providers it was sent to. Key attributes:
  the prompt text, the submitting user, the selected providers, the model explicitly chosen for
  each selected provider (FR-020), and a timestamp. Has one Provider Result per selected provider.
- **Provider Result**: One provider's outcome for a comparison. Key attributes: which provider, the
  response content (if any), the outcome state (success, empty, error, or timeout), and the recorded
  telemetry (response time, time-to-first-token, input/output token counts, exact model identifier —
  each when reported, FR-019). Belongs to a Comparison.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A signed-in user can go from entering a prompt to seeing side-by-side panels for all
  selected providers begin populating within 2 seconds of submission (excluding provider thinking
  time).
- **SC-002**: In a comparison where one provider fails or times out, 100% of the other selected
  providers' successful responses are still displayed.
- **SC-003**: A new user can complete a full comparison (sign in, enter prompt, pick providers,
  submit, read results) in under 3 minutes on first attempt without external guidance.
- **SC-004**: 100% of attempts to reach the comparison or history screens while signed out are
  denied.
- **SC-005**: 100% of a user's completed comparisons appear in their own history and 0% appear in
  any other user's history.
- **SC-006**: The platform supports comparing up to four providers in a single prompt submission.
- **SC-007**: A user can locate and reopen a specific past comparison from history in under 1 minute.

## Assumptions

- **Account provisioning**: User accounts exist or are created out of band for this MVP; self-service
  public registration is not required by the scope ceiling. Minimal username/password sign-in is the
  only required auth flow.
- **Single concurrent comparison per user**: A user runs one comparison at a time in the foreground;
  rapid resubmission replaces the in-flight comparison rather than running many in parallel.
- **In-flight on logout**: A comparison still running when the user signs out is not guaranteed to be
  saved; only completed comparisons are guaranteed in history.
- **History scope**: History retains comparisons indefinitely for the MVP (no retention/expiry
  policy required for the academic prototype).
- **Provider set is fixed**: The five named providers define the supported set; adding a sixth is out
  of scope unless the spec is amended.
- **Response presentation**: Responses are shown as text. Rich rendering (markdown, code
  highlighting, streaming token-by-token) is a desirable enhancement, not a hard requirement for the
  MVP, and its inclusion is deferred to planning.
- **Single user concurrency target**: As a solo academic prototype, the platform targets demo-scale
  concurrent usage (up to ~5 simultaneous users), not production-scale load.
- **Connectivity**: Users have stable internet connectivity and modern browsers.
