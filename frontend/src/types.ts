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
  /** Model used when the user picks none. Always present in `models`. */
  defaultModel: string
  /** Selectable models — curated list ∪ the provider's live list. */
  models: string[]
  /** `live` when the provider's list API contributed entries. */
  source: 'live' | 'curated'
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
