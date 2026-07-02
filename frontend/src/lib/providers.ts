import type { ProviderCatalogEntry, ProviderId } from '../types'

export interface ProviderMeta {
  id: ProviderId
  /** Human label shown in chips, lanes and history. */
  label: string
  /** Tailwind colour token name for this provider's brand hue. */
  hue: string
}

/** Provider metadata, ordered for display. */
export const PROVIDERS: ProviderMeta[] = [
  { id: 'GEMINI', label: 'Gemini', hue: 'gemini' },
  { id: 'CHATGPT', label: 'ChatGPT', hue: 'chatgpt' },
  { id: 'CLAUDE', label: 'Claude', hue: 'claude' },
  { id: 'GROK', label: 'Grok', hue: 'grok' },
  { id: 'DEEPSEEK', label: 'DeepSeek', hue: 'deepseek' },
]

const BY_ID: Record<ProviderId, ProviderMeta> = PROVIDERS.reduce(
  (acc, p) => {
    acc[p.id] = p
    return acc
  },
  {} as Record<ProviderId, ProviderMeta>,
)

/** Look up provider metadata by id. */
export function providerMeta(id: ProviderId): ProviderMeta {
  return BY_ID[id]
}

/** Maximum providers selectable per comparison (FR-005). */
export const MAX_PROVIDERS = 4

/** Maximum prompt length (quickstart: MAX_PROMPT_LEN). */
export const MAX_PROMPT_LEN = 8000

/**
 * Static default model per provider — the composer's safety net while
 * `GET /api/providers` is in flight or failed (FR-020). Mirrors the backend's
 * curated defaults so the combo box always has at least one valid option.
 */
export const DEFAULT_MODELS: Record<ProviderId, string> = {
  GEMINI: 'gemini-2.5-flash',
  CHATGPT: 'gpt-4o-mini',
  CLAUDE: 'claude-3-5-sonnet-latest',
  GROK: 'grok-2-latest',
  DEEPSEEK: 'deepseek-chat',
}

/**
 * Build the static fallback catalog: one curated entry per provider, holding
 * just its default model. Providers are assumed configured — the "não
 * configurado" hint only ever comes from real catalog data. A catalog fetch
 * failure must never block composing.
 */
export function fallbackCatalog(): Record<ProviderId, ProviderCatalogEntry> {
  const record = {} as Record<ProviderId, ProviderCatalogEntry>
  for (const p of PROVIDERS) {
    record[p.id] = {
      provider: p.id,
      configured: true,
      defaultModel: DEFAULT_MODELS[p.id],
      models: [DEFAULT_MODELS[p.id]],
      source: 'curated',
    }
  }
  return record
}
