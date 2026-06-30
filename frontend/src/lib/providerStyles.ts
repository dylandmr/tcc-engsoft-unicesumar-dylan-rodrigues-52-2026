import type { ProviderId } from '../types'

export interface ProviderStyle {
  /** Brand-hued dot background. */
  dot: string
  /** Brand-hued text. */
  text: string
  /** Brand-hued border (selected chip / lane outline). */
  border: string
  /** Brand-hued top accent bar of a results lane. */
  bar: string
}

/**
 * Literal Tailwind class names per provider. Kept literal (not interpolated)
 * so the Tailwind scanner emits every utility used here.
 */
export const PROVIDER_STYLES: Record<ProviderId, ProviderStyle> = {
  GEMINI: {
    dot: 'bg-gemini',
    text: 'text-gemini',
    border: 'border-gemini',
    bar: 'bg-gemini',
  },
  CHATGPT: {
    dot: 'bg-chatgpt',
    text: 'text-chatgpt',
    border: 'border-chatgpt',
    bar: 'bg-chatgpt',
  },
  CLAUDE: {
    dot: 'bg-claude',
    text: 'text-claude',
    border: 'border-claude',
    bar: 'bg-claude',
  },
  GROK: {
    dot: 'bg-grok',
    text: 'text-grok',
    border: 'border-grok',
    bar: 'bg-grok',
  },
  DEEPSEEK: {
    dot: 'bg-deepseek',
    text: 'text-deepseek',
    border: 'border-deepseek',
    bar: 'bg-deepseek',
  },
}
