import type { ProviderId } from '../types'

export interface ProviderStyle {
  /** Brand-hued dot background. */
  dot: string
  /** Brand-hued text. */
  text: string
  /** Brand-hued border (armed card / lane outline). */
  border: string
  /** Brand-hued top accent bar of a results lane / armed contender card. */
  bar: string
  /** Soft brand-hued glow on the accent bar while the lane streams. */
  glow: string
  /** Translucent brand-hued fill for an armed contender card. */
  chipBg: string
  /** Wide, soft brand-hued halo around an armed contender card. */
  cardGlow: string
  /** Dimmed top accent bar of an idle contender card. */
  barDim: string
  /** Dimmed brand dot of an idle contender card. */
  dotDim: string
  /** Order badge (1º, 2º…) tint on an armed contender card. */
  pill: string
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
    cardGlow: 'shadow-[0_0_28px_0] shadow-gemini/25',
    barDim: 'bg-gemini/45',
    dotDim: 'bg-gemini/40',
    pill: 'border-gemini/40 bg-gemini/15',
  },
  CHATGPT: {
    dot: 'bg-chatgpt',
    text: 'text-chatgpt',
    border: 'border-chatgpt',
    bar: 'bg-chatgpt',
    glow: 'shadow-[0_0_14px_1px] shadow-chatgpt/60',
    chipBg: 'bg-chatgpt/10',
    cardGlow: 'shadow-[0_0_28px_0] shadow-chatgpt/25',
    barDim: 'bg-chatgpt/45',
    dotDim: 'bg-chatgpt/40',
    pill: 'border-chatgpt/40 bg-chatgpt/15',
  },
  CLAUDE: {
    dot: 'bg-claude',
    text: 'text-claude',
    border: 'border-claude',
    bar: 'bg-claude',
    glow: 'shadow-[0_0_14px_1px] shadow-claude/60',
    chipBg: 'bg-claude/10',
    cardGlow: 'shadow-[0_0_28px_0] shadow-claude/25',
    barDim: 'bg-claude/45',
    dotDim: 'bg-claude/40',
    pill: 'border-claude/40 bg-claude/15',
  },
  GROK: {
    dot: 'bg-grok',
    text: 'text-grok',
    border: 'border-grok',
    bar: 'bg-grok',
    glow: 'shadow-[0_0_14px_1px] shadow-grok/60',
    chipBg: 'bg-grok/10',
    cardGlow: 'shadow-[0_0_28px_0] shadow-grok/25',
    barDim: 'bg-grok/45',
    dotDim: 'bg-grok/40',
    pill: 'border-grok/40 bg-grok/15',
  },
  DEEPSEEK: {
    dot: 'bg-deepseek',
    text: 'text-deepseek',
    border: 'border-deepseek',
    bar: 'bg-deepseek',
    glow: 'shadow-[0_0_14px_1px] shadow-deepseek/60',
    chipBg: 'bg-deepseek/10',
    cardGlow: 'shadow-[0_0_28px_0] shadow-deepseek/25',
    barDim: 'bg-deepseek/45',
    dotDim: 'bg-deepseek/40',
    pill: 'border-deepseek/40 bg-deepseek/15',
  },
}
