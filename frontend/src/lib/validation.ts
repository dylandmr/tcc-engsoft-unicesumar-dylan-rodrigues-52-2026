import type { ModelSelection, ProviderId } from '../types'
import { MAX_PROMPT_LEN, MAX_PROVIDERS } from './providers'

/** Client-side validation codes (mirror the POST /api/comparisons contract). */
export type ValidationError =
  | 'empty_prompt'
  | 'prompt_too_long'
  | 'no_providers'
  | 'too_many_providers'
  | 'duplicate_provider'
  | 'missing_model'

/** User-facing message for each validation code (FR-006). */
export const VALIDATION_MESSAGES: Record<ValidationError, string> = {
  empty_prompt: 'Digite um prompt antes de comparar.',
  prompt_too_long: `O prompt é muito longo (máx. ${MAX_PROMPT_LEN} caracteres).`,
  no_providers: 'Selecione pelo menos um modelo para comparar.',
  too_many_providers: `Selecione no máximo ${MAX_PROVIDERS} modelos.`,
  duplicate_provider: 'Cada modelo só pode ser selecionado uma vez.',
  // The cards are "modelos" in the UI voice; the combo picks that model's
  // version — no default exists, the user must choose (FR-020).
  missing_model: 'Escolha a versão de cada modelo selecionado.',
}

/**
 * Validate a comparison submission. Returns the first failing code, or `null`
 * when the input is valid.
 */
export function validateComparison(
  prompt: string,
  providers: ProviderId[],
  models: ModelSelection,
): ValidationError | null {
  if (prompt.trim() === '') return 'empty_prompt'
  if (prompt.length > MAX_PROMPT_LEN) return 'prompt_too_long'
  if (providers.length === 0) return 'no_providers'
  if (providers.length > MAX_PROVIDERS) return 'too_many_providers'
  if (new Set(providers).size !== providers.length) return 'duplicate_provider'
  // Every selected provider needs an explicit model pick (FR-020).
  if (providers.some((p) => !models[p])) return 'missing_model'
  return null
}
