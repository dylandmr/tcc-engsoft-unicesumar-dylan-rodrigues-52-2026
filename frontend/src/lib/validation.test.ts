import { describe, expect, it } from 'vitest'
import { VALIDATION_MESSAGES, validateComparison } from './validation'

describe('validateComparison', () => {
  it('rejects an empty/whitespace prompt', () => {
    expect(validateComparison('   ', ['CLAUDE'], {})).toBe('empty_prompt')
  })

  it('rejects an over-long prompt', () => {
    expect(validateComparison('x'.repeat(8001), ['CLAUDE'], {})).toBe(
      'prompt_too_long',
    )
  })

  it('rejects when no providers are selected', () => {
    expect(validateComparison('hi', [], {})).toBe('no_providers')
  })

  it('rejects more than four providers', () => {
    expect(
      validateComparison(
        'hi',
        ['CLAUDE', 'CHATGPT', 'GEMINI', 'GROK', 'DEEPSEEK'],
        {},
      ),
    ).toBe('too_many_providers')
  })

  it('rejects duplicate providers', () => {
    expect(validateComparison('hi', ['CLAUDE', 'CLAUDE'], {})).toBe(
      'duplicate_provider',
    )
  })

  it('rejects a selected provider without a chosen model (FR-020)', () => {
    expect(validateComparison('hi', ['CLAUDE'], {})).toBe('missing_model')
  })

  it('rejects a blank model pick', () => {
    expect(validateComparison('hi', ['CLAUDE'], { CLAUDE: '' })).toBe(
      'missing_model',
    )
  })

  it('rejects when only some providers have a chosen model', () => {
    expect(
      validateComparison('hi', ['CLAUDE', 'GEMINI'], {
        CLAUDE: 'claude-3-5-sonnet-latest',
      }),
    ).toBe('missing_model')
  })

  it('accepts a valid submission with a model per provider', () => {
    expect(
      validateComparison('hi', ['CLAUDE', 'CHATGPT'], {
        CLAUDE: 'claude-3-5-sonnet-latest',
        CHATGPT: 'gpt-4o-mini',
      }),
    ).toBeNull()
  })

  it('exposes a message for every code', () => {
    expect(VALIDATION_MESSAGES.empty_prompt).toMatch(/prompt/i)
    expect(VALIDATION_MESSAGES.missing_model).toMatch(/versão/i)
  })
})
