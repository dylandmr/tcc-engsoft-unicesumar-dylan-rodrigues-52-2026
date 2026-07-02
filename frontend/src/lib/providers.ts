import type { ProviderId } from '../types'

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
