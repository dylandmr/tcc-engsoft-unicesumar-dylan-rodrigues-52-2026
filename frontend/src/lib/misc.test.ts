import { describe, expect, it } from 'vitest'
import { cn } from './cn'
import { providerMeta, PROVIDERS } from './providers'
import { PROVIDER_STYLES } from './providerStyles'
import { laneStatusInfo } from './laneStatus'
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

const lane = (over: Partial<LaneState>): LaneState => ({
  provider: 'CLAUDE',
  status: 'live',
  text: '',
  errorMessage: null,
  responseTimeMs: null,
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
