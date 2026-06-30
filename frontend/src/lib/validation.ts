import type { ProviderId } from '../types'
import { MAX_PROMPT_LEN, MAX_PROVIDERS } from './providers'

/** Client-side validation codes (mirror the POST /api/comparisons contract). */
export type ValidationError =
  | 'empty_prompt'
  | 'prompt_too_long'
  | 'no_providers'
  | 'too_many_providers'
  | 'duplicate_provider'

/** User-facing message for each validation code (FR-006). */
export const VALIDATION_MESSAGES: Record<ValidationError, string> = {
  empty_prompt: 'Enter a prompt before running a comparison.',
  prompt_too_long: `Prompt is too long (max ${MAX_PROMPT_LEN} characters).`,
  no_providers: 'Select at least one model to compare.',
  too_many_providers: `Select at most ${MAX_PROVIDERS} models.`,
  duplicate_provider: 'Each model can only be selected once.',
}

/**
 * Validate a comparison submission. Returns the first failing code, or `null`
 * when the input is valid.
 */
export function validateComparison(
  prompt: string,
  providers: ProviderId[],
): ValidationError | null {
  if (prompt.trim() === '') return 'empty_prompt'
  if (prompt.length > MAX_PROMPT_LEN) return 'prompt_too_long'
  if (providers.length === 0) return 'no_providers'
  if (providers.length > MAX_PROVIDERS) return 'too_many_providers'
  if (new Set(providers).size !== providers.length) return 'duplicate_provider'
  return null
}
