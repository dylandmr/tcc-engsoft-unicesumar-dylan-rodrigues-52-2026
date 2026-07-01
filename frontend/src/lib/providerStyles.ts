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
  /** Soft brand-hued glow on the accent bar while the lane streams. */
  glow: string
  /** Translucent brand-hued fill for a selected composer chip. */
  chipBg: string
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
    glow: 'shadow-[0_0_14px_1px] shadow-gemini/60',
    chipBg: 'bg-gemini/10',
  },
  CHATGPT: {
    dot: 'bg-chatgpt',
    text: 'text-chatgpt',
    border: 'border-chatgpt',
    bar: 'bg-chatgpt',
    glow: 'shadow-[0_0_14px_1px] shadow-chatgpt/60',
    chipBg: 'bg-chatgpt/10',
  },
  CLAUDE: {
    dot: 'bg-claude',
    text: 'text-claude',
    border: 'border-claude',
    bar: 'bg-claude',
    glow: 'shadow-[0_0_14px_1px] shadow-claude/60',
    chipBg: 'bg-claude/10',
  },
  GROK: {
    dot: 'bg-grok',
    text: 'text-grok',
    border: 'border-grok',
    bar: 'bg-grok',
    glow: 'shadow-[0_0_14px_1px] shadow-grok/60',
    chipBg: 'bg-grok/10',
  },
  DEEPSEEK: {
    dot: 'bg-deepseek',
    text: 'text-deepseek',
    border: 'border-deepseek',
    bar: 'bg-deepseek',
    glow: 'shadow-[0_0_14px_1px] shadow-deepseek/60',
    chipBg: 'bg-deepseek/10',
  },
}
