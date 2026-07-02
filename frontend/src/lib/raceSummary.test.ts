import { describe, expect, it } from 'vitest'
import { buildRaceSummary } from './raceSummary'
import {
  arenaReducer,
  initArena,
  type ArenaState,
  type LaneState,
} from '../hooks/arenaReducer'
import type { ProviderId, ProviderResult } from '../types'

const lane = (provider: ProviderId, over: Partial<LaneState>): LaneState => ({
  provider,
  status: 'done',
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

/** Build a done ArenaState whose selection order is the given lane order. */
function state(lanes: LaneState[]): ArenaState {
  const record = {} as Record<ProviderId, LaneState>
  for (const l of lanes) record[l.provider] = l
  return {
    order: lanes.map((l) => l.provider),
    lanes: record,
    done: true,
    analysis: { phase: 'idle' },
  }
}

describe('buildRaceSummary ranking', () => {
  it('ranks responded lanes ascending by responseTimeMs, not selection order', () => {
    const s = state([
      lane('GEMINI', { responseTimeMs: 1840 }),
      lane('CHATGPT', { responseTimeMs: 2500 }),
      lane('CLAUDE', { responseTimeMs: 970 }),
    ])
    const summary = buildRaceSummary(s)
    expect(summary.rows.map((r) => r.provider)).toEqual([
      'CLAUDE',
      'GEMINI',
      'CHATGPT',
    ])
    expect(summary.rows.map((r) => r.rank)).toEqual([1, 2, 3])
    expect(summary.winner).toBe('CLAUDE')
    expect(summary.hasData).toBe(true)
  })

  it('breaks latency ties by selection order (stable)', () => {
    const s = state([
      lane('GROK', { responseTimeMs: 1000 }),
      lane('DEEPSEEK', { responseTimeMs: 1000 }),
    ])
    const rows = buildRaceSummary(s).rows
    expect(rows.map((r) => r.provider)).toEqual(['GROK', 'DEEPSEEK'])
    expect(rows[1].deltaMs).toBe(0)
  })

  it('treats an EMPTY response as a ranked responder', () => {
    const s = state([lane('GEMINI', { status: 'empty', responseTimeMs: 700 })])
    const summary = buildRaceSummary(s)
    expect(summary.rows[0].rank).toBe(1)
    expect(summary.winner).toBe('GEMINI')
  })

  it('computes the gap to the winner (null for the winner itself)', () => {
    const s = state([
      lane('GEMINI', { responseTimeMs: 1000 }),
      lane('CLAUDE', { responseTimeMs: 1420 }),
    ])
    const rows = buildRaceSummary(s).rows
    expect(rows[0].deltaMs).toBeNull()
    expect(rows[1].deltaMs).toBe(420)
  })

  it('scales latency bars against the slowest ranked lane', () => {
    const s = state([
      lane('GEMINI', { responseTimeMs: 1000 }),
      lane('CLAUDE', { responseTimeMs: 2000 }),
    ])
    const rows = buildRaceSummary(s).rows
    expect(rows[0].barFraction).toBe(0.5)
    expect(rows[1].barFraction).toBe(1)
  })

  it('guards the bar against a 0ms slowest lane (division by zero → 1)', () => {
    const s = state([lane('GEMINI', { responseTimeMs: 0 })])
    expect(buildRaceSummary(s).rows[0].barFraction).toBe(1)
  })
})

describe('buildRaceSummary tokens/s and chars', () => {
  it('computes tokens/s over the first-token → completion window', () => {
    const s = state([
      lane('GEMINI', {
        responseTimeMs: 2400,
        firstTokenMs: 400,
        outputTokens: 100,
        text: 'abcd',
      }),
    ])
    const row = buildRaceSummary(s).rows[0]
    expect(row.tokensPerSecond).toBe(50) // 100 tok / 2.0s
    expect(row.chars).toBe(4)
  })

  it('yields null tokens/s without an output token count', () => {
    const s = state([
      lane('GEMINI', { responseTimeMs: 2400, firstTokenMs: 400 }),
    ])
    expect(buildRaceSummary(s).rows[0].tokensPerSecond).toBeNull()
  })

  it('yields null tokens/s without a first-token time', () => {
    const s = state([
      lane('GEMINI', { responseTimeMs: 2400, outputTokens: 100 }),
    ])
    expect(buildRaceSummary(s).rows[0].tokensPerSecond).toBeNull()
  })

  it('yields null tokens/s for a zero or negative streaming window', () => {
    const zero = state([
      lane('GEMINI', {
        responseTimeMs: 400,
        firstTokenMs: 400,
        outputTokens: 100,
      }),
    ])
    expect(buildRaceSummary(zero).rows[0].tokensPerSecond).toBeNull()
    const negative = state([
      lane('GEMINI', {
        responseTimeMs: 300,
        firstTokenMs: 400,
        outputTokens: 100,
      }),
    ])
    expect(buildRaceSummary(negative).rows[0].tokensPerSecond).toBeNull()
  })
})

describe('buildRaceSummary row classes and ordering', () => {
  it('lists responded lanes without a recorded time after all timed rows', () => {
    const s = state([
      lane('GEMINI', { responseTimeMs: null, outputTokens: 12 }),
      lane('CLAUDE', { responseTimeMs: 1500 }),
    ])
    const rows = buildRaceSummary(s).rows
    expect(rows.map((r) => r.provider)).toEqual(['CLAUDE', 'GEMINI'])
    expect(rows[1]).toMatchObject({
      kind: 'untimed',
      rank: null,
      deltaMs: null,
      barFraction: null,
      tokensPerSecond: null, // no window without a total time
      outputTokens: 12,
    })
  })

  it('keeps faults unranked with their error message, and disabled last', () => {
    const s = state([
      lane('DEEPSEEK', {
        status: 'disabled',
        errorMessage: 'provider_not_configured',
      }),
      lane('GEMINI', { responseTimeMs: 1000 }),
      lane('GROK', { status: 'error', errorMessage: 'rate_limited' }),
      lane('CLAUDE', { status: 'timeout', errorMessage: 'sem resposta' }),
    ])
    const rows = buildRaceSummary(s).rows
    expect(rows.map((r) => [r.provider, r.kind])).toEqual([
      ['GEMINI', 'ranked'],
      ['GROK', 'fault'],
      ['CLAUDE', 'fault'],
      ['DEEPSEEK', 'disabled'],
    ])
    expect(rows[1].errorMessage).toBe('rate_limited')
    expect(rows[1].rank).toBeNull()
  })

  it('reports no data when no lane produced a timed response', () => {
    const s = state([
      lane('GEMINI', { status: 'error', errorMessage: 'boom' }),
      lane('CLAUDE', { status: 'timeout', errorMessage: 'sem resposta' }),
    ])
    const summary = buildRaceSummary(s)
    expect(summary.hasData).toBe(false)
    expect(summary.winner).toBeNull()
    expect(summary.rows.map((r) => r.kind)).toEqual(['fault', 'fault'])
  })

  it('ignores lanes still streaming (mid-run derivation)', () => {
    const s = state([
      lane('GEMINI', { status: 'live' }),
      lane('CLAUDE', { responseTimeMs: 900 }),
    ])
    const summary = buildRaceSummary(s)
    expect(summary.rows.map((r) => r.provider)).toEqual(['CLAUDE'])
    expect(summary.winner).toBe('CLAUDE')
  })
})

describe('buildRaceSummary replay correctness', () => {
  const result = (over: Partial<ProviderResult>): ProviderResult => ({
    provider: 'CLAUDE',
    outcome: 'SUCCESS',
    responseText: 'oi',
    errorMessage: null,
    responseTimeMs: 1200,
    firstTokenMs: null,
    inputTokens: null,
    outputTokens: null,
    model: null,
    ...over,
  })

  it('derives the winner from responseTimeMs even when results arrive out of order', () => {
    // History replay emits persisted results in selection order — here the
    // slower GEMINI arrives first. The winner must still be CLAUDE.
    let s = initArena(['GEMINI', 'CLAUDE'])
    s = arenaReducer(s, {
      type: 'result',
      result: result({ provider: 'GEMINI', responseTimeMs: 2500 }),
    })
    expect(buildRaceSummary(s).winner).toBe('GEMINI') // provisional
    s = arenaReducer(s, {
      type: 'result',
      result: result({ provider: 'CLAUDE', responseTimeMs: 1200 }),
    })
    expect(buildRaceSummary(s).winner).toBe('CLAUDE')
  })
})
