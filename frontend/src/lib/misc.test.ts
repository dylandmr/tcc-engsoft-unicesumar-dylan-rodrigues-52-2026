import { afterEach, describe, expect, it, vi } from 'vitest'
import { cn } from './cn'
import {
  DEFAULT_MODELS,
  fallbackCatalog,
  providerMeta,
  PROVIDERS,
} from './providers'
import { PROVIDER_STYLES } from './providerStyles'
import { laneStatusInfo } from './laneStatus'
import { prefersReducedMotion } from './motion'
import type { LaneState } from '../hooks/arenaReducer'

describe('cn', () => {
  it('joins truthy fragments and drops falsy ones', () => {
    expect(cn('a', false, null, undefined, 'b')).toBe('a b')
  })
})

describe('providers', () => {
  it('looks up metadata by id', () => {
    expect(providerMeta('CLAUDE').label).toBe('Claude')
  })
  it('has a style entry for every provider', () => {
    for (const p of PROVIDERS) {
      expect(PROVIDER_STYLES[p.id].dot).toContain(p.hue)
    }
  })
})

describe('fallbackCatalog', () => {
  it('builds a curated, default-only, assumed-configured entry per provider', () => {
    const catalog = fallbackCatalog()
    for (const p of PROVIDERS) {
      expect(catalog[p.id]).toEqual({
        provider: p.id,
        configured: true,
        defaultModel: DEFAULT_MODELS[p.id],
        models: [DEFAULT_MODELS[p.id]],
        source: 'curated',
      })
    }
  })
})

describe('prefersReducedMotion', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reflects the reduce media query', () => {
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches: true }))
    expect(prefersReducedMotion()).toBe(true)
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches: false }))
    expect(prefersReducedMotion()).toBe(false)
  })

  it('treats a missing matchMedia as no preference', () => {
    vi.stubGlobal('matchMedia', undefined)
    expect(prefersReducedMotion()).toBe(false)
  })
})

const lane = (over: Partial<LaneState>): LaneState => ({
  provider: 'CLAUDE',
  status: 'live',
  text: '',
  errorMessage: null,
  responseTimeMs: null,
  firstTokenMs: null,
  inputTokens: null,
  outputTokens: null,
  model: null,
  elapsedMs: 0,
  first: false,
  ...over,
})

describe('laneStatusInfo', () => {
  it('flags the first responder', () => {
    expect(laneStatusInfo(lane({ first: true })).label).toBe(
      'primeiro a responder',
    )
  })
  it('maps each lane status to a label', () => {
    expect(laneStatusInfo(lane({ status: 'live' })).label).toMatch(/ao vivo/)
    expect(laneStatusInfo(lane({ status: 'done' })).label).toBe('concluído')
    expect(laneStatusInfo(lane({ status: 'empty' })).label).toMatch(/vazia/)
    expect(laneStatusInfo(lane({ status: 'error' })).toneClass).toBe(
      'text-error',
    )
    expect(laneStatusInfo(lane({ status: 'timeout' })).toneClass).toBe(
      'text-timeout',
    )
    expect(laneStatusInfo(lane({ status: 'disabled' })).label).toBe(
      'não configurado',
    )
  })
})
