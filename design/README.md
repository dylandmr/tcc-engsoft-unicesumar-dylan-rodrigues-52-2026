# Prompt Arena — Design Mocks ("Observation Deck")

Approval mocks for the four MVP screens (task **T071**). Source of truth:
**[Figma — Prompt Arena · Observation Deck](https://www.figma.com/design/CjHEyeFxsEigAQvcDcQWGr)**

| Screen | File |
|---|---|
| Login | [`01-login.png`](./01-login.png) |
| Composer | [`02-composer.png`](./02-composer.png) |
| Results — Arena (signature) | [`03-results-arena.png`](./03-results-arena.png) |
| History | [`04-history.png`](./04-history.png) |

## Direction

A dark-first **telemetry "arena"**: each provider runs in its own brand-hued lane and streams its
answer live with a mono latency readout. Per-provider failures are isolated and shown in-lane (see
the amber Gemini timeout on the Results screen) — the resilience requirement made visible.

**Tokens** (Figma variable collection `Observation Deck`, → Tailwind in T072):

- `bg/void` `#0B0E14` · `bg/deck` `#141926` · `bg/line` `#232B3A`
- `text/mist` `#8A94A6` · `text/bright` `#EAF0FF`
- `accent/ignition` `#FF8A3D` (launch / live / focus)
- `provider/*` — Claude `#D97757`, ChatGPT `#10A37F`, Gemini `#6C7CF0`, Grok `#E6E8EC`, DeepSeek `#4D6BFE`
- `state/error` `#F2555A` · `state/timeout` `#E0A33E`

**Type**: Space Grotesk (display) · IBM Plex Sans (body) · IBM Plex Mono (data / telemetry).

The signature element — the live race-lanes results view with an orchestrated launch sequence and
streaming reveal — is realized fully in code (Framer Motion); these static frames establish the
look, palette, type, and layout.
