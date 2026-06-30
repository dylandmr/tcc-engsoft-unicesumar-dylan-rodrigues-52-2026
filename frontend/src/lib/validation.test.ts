import { describe, expect, it } from 'vitest'
import { VALIDATION_MESSAGES, validateComparison } from './validation'

describe('validateComparison', () => {
  it('rejects an empty/whitespace prompt', () => {
    expect(validateComparison('   ', ['CLAUDE'])).toBe('empty_prompt')
  })

  it('rejects an over-long prompt', () => {
    expect(validateComparison('x'.repeat(8001), ['CLAUDE'])).toBe(
      'prompt_too_long',
    )
  })

  it('rejects when no providers are selected', () => {
    expect(validateComparison('hi', [])).toBe('no_providers')
  })

  it('rejects more than four providers', () => {
    expect(
      validateComparison('hi', [
        'CLAUDE',
        'CHATGPT',
        'GEMINI',
        'GROK',
        'DEEPSEEK',
      ]),
    ).toBe('too_many_providers')
  })

  it('rejects duplicate providers', () => {
    expect(validateComparison('hi', ['CLAUDE', 'CLAUDE'])).toBe(
      'duplicate_provider',
    )
  })

  it('accepts a valid submission', () => {
    expect(validateComparison('hi', ['CLAUDE', 'CHATGPT'])).toBeNull()
  })

  it('exposes a message for every code', () => {
    expect(VALIDATION_MESSAGES.empty_prompt).toMatch(/prompt/i)
  })
})
