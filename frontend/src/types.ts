/** The five supported providers, matching the API contract enum exactly. */
export type ProviderId = 'GEMINI' | 'CHATGPT' | 'CLAUDE' | 'GROK' | 'DEEPSEEK'

/** Per-provider outcome states from the SSE `result` event. */
export type Outcome = 'SUCCESS' | 'EMPTY' | 'ERROR' | 'TIMEOUT'

/** Payload of an SSE `result` event (contract: rest-api.md). */
export interface ProviderResult {
  provider: ProviderId
  outcome: Outcome
  responseText: string | null
  errorMessage: string | null
  responseTimeMs: number | null
  /** Time-to-first-token in ms, same clock as responseTimeMs (FR-019). */
  firstTokenMs: number | null
  /** Provider-reported prompt token count (FR-019). */
  inputTokens: number | null
  /** Provider-reported completion token count (FR-019). */
  outputTokens: number | null
  /** Exact model id the provider reports as having answered (FR-019). */
  model: string | null
}

/** Payload of an SSE `chunk` event — an incremental text delta for one provider. */
export interface ChunkEvent {
  provider: ProviderId
  delta: string
}

/** Payload of the SSE `done` event. */
export interface DoneEvent {
  comparisonId: string
  completed: number
}

/** Payload of an SSE `analysis-chunk` event — an incremental judge text delta (FR-021). */
export interface AnalysisChunkEvent {
  delta: string
}

/**
 * Terminal payload of the SSE `analysis` event (FR-021). On judge failure,
 * `text` is null and `errorMessage` is set — nothing is persisted, so the
 * user may retry with any judge.
 */
export interface AnalysisResult {
  text: string | null
  errorMessage: string | null
  /** The judge the user picked (FR-020: no default judge). */
  provider: ProviderId
  model: string
  /** Neutral anonymization labels → provider, e.g. { A: 'GEMINI' }. */
  labels: Record<string, ProviderId>
}

/** A successfully recorded analysis, as persisted with the comparison (FR-021). */
export interface RecordedAnalysis {
  text: string
  provider: ProviderId
  model: string
  labels: Record<string, ProviderId>
}

/** Response of POST /api/comparisons. */
export interface CreatedComparison {
  comparisonId: string
  providers: ProviderId[]
}

/** Requested model per selected provider (POST /api/comparisons, FR-020). */
export type ModelSelection = Partial<Record<ProviderId, string>>

/** One provider's entry in GET /api/providers (FR-020). */
export interface ProviderCatalogEntry {
  provider: ProviderId
  /** Whether the server holds an API key for this provider. */
  configured: boolean
  /**
   * Exactly the models the provider's own API reports as available — empty
   * for unconfigured providers and on live-fetch failure. A provider with no
   * models cannot join a comparison.
   */
  models: string[]
}

/**
 * One provider's aggregate over the caller's recorded runs, from
 * GET /api/comparisons/stats (FR-023). `runs` = successes + empties +
 * errors + timeouts; each average is null when no run carries the values
 * it needs, and `telemetryRuns` is the honest basis the SPA captions.
 */
export interface ProviderStats {
  provider: ProviderId
  runs: number
  successes: number
  empties: number
  errors: number
  timeouts: number
  telemetryRuns: number
  avgResponseTimeMs: number | null
  avgFirstTokenMs: number | null
  avgTokensPerSecond: number | null
}

/** A summary row from GET /api/comparisons. */
export interface ComparisonSummary {
  id: string
  prompt: string
  providers: ProviderId[]
  createdAt: string
}

/** The signed-in user. */
export interface User {
  username: string
}
