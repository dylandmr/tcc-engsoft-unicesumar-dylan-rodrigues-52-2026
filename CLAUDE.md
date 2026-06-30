<!-- SPECKIT START -->
## Active feature: Prompt Arena MVP (`001-prompt-arena-mvp`)

Read the current plan and its design artifacts for technologies, project structure, shell
commands, and other context:

- Plan: `specs/001-prompt-arena-mvp/plan.md`
- Spec: `specs/001-prompt-arena-mvp/spec.md`
- Research (tech decisions): `specs/001-prompt-arena-mvp/research.md`
- Data model: `specs/001-prompt-arena-mvp/data-model.md`
- API contract: `specs/001-prompt-arena-mvp/contracts/rest-api.md`
- Quickstart/validation: `specs/001-prompt-arena-mvp/quickstart.md`

**Stack** (authoritative = `.specify/memory/constitution.md`): React 18 + Vite (TypeScript) SPA ·
Java 21 / Spring Boot 3.x REST · SQLite via Spring Data JPA · Docker (`docker compose up`, local).
Provider fan-out via a uniform `LlmProvider` interface (OpenAI Java SDK ×3 base URLs + Anthropic +
Google GenAI SDKs), concurrent with per-provider isolation, streamed to the SPA over SSE.
<!-- SPECKIT END -->
