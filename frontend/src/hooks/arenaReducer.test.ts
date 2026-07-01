import { describe, expect, it } from 'vitest'
import { arenaReducer, initArena } from './arenaReducer'
import type { ProviderResult } from '../types'

const result = (over: Partial<ProviderResult>): ProviderResult => ({
  provider: 'CLAUDE',
  outcome: 'SUCCESS',
  responseText: 'hi',
  errorMessage: null,
  responseTimeMs: 1840,
  ...over,
})

describe('arenaReducer', () => {
  const start = initArena(['CLAUDE', 'CHATGPT'])

  it('initialises all lanes live', () => {
    expect(start.lanes.CLAUDE.status).toBe('live')
    expect(start.order).toEqual(['CLAUDE', 'CHATGPT'])
  })

  it('tick advances elapsed only for live lanes', () => {
    const done = arenaReducer(start, { type: 'result', result: result({}) })
    const ticked = arenaReducer(done, { type: 'tick', elapsedMs: 500 })
    expect(ticked.lanes.CHATGPT.elapsedMs).toBe(500) // still live
    expect(ticked.lanes.CLAUDE.elapsedMs).toBe(1840) // frozen at response time
  })

  it('marks the first responder and maps outcome to status', () => {
    const s1 = arenaReducer(start, { type: 'result', result: result({}) })
    expect(s1.lanes.CLAUDE.status).toBe('done')
    expect(s1.lanes.CLAUDE.first).toBe(true)
    expect(s1.firstAssigned).toBe(true)

    const s2 = arenaReducer(s1, {
      type: 'result',
      result: result({ provider: 'CHATGPT' }),
    })
    expect(s2.lanes.CHATGPT.first).toBe(false) // not the first
  })

  it('treats an EMPTY outcome as a response (no text)', () => {
    const s = arenaReducer(start, {
      type: 'result',
      result: result({ outcome: 'EMPTY', responseText: null }),
    })
    expect(s.lanes.CLAUDE.status).toBe('empty')
    expect(s.lanes.CLAUDE.text).toBe('')
    expect(s.lanes.CLAUDE.first).toBe(true)
  })

  it('does not award first to an error/timeout, and falls back to elapsed', () => {
    const ticked = arenaReducer(start, { type: 'tick', elapsedMs: 300 })
    const s = arenaReducer(ticked, {
      type: 'result',
      result: result({
        outcome: 'TIMEOUT',
        responseText: null,
        responseTimeMs: null,
      }),
    })
    expect(s.lanes.CLAUDE.status).toBe('timeout')
    expect(s.lanes.CLAUDE.first).toBe(false)
    expect(s.firstAssigned).toBe(false)
    expect(s.lanes.CLAUDE.elapsedMs).toBe(300)
  })

  it('appends streamed chunk deltas to the lane text while live', () => {
    const a = arenaReducer(start, {
      type: 'chunk',
      chunk: { provider: 'CLAUDE', delta: 'Hel' },
    })
    const b = arenaReducer(a, {
      type: 'chunk',
      chunk: { provider: 'CLAUDE', delta: 'lo' },
    })
    expect(b.lanes.CLAUDE.text).toBe('Hello')
    expect(b.lanes.CLAUDE.status).toBe('live')
  })

  it('maps an unconfigured provider to a disabled lane, not an error', () => {
    const s = arenaReducer(start, {
      type: 'result',
      result: result({
        outcome: 'ERROR',
        responseText: null,
        errorMessage: 'provider_not_configured',
        responseTimeMs: null,
      }),
    })
    expect(s.lanes.CLAUDE.status).toBe('disabled')
    expect(s.lanes.CLAUDE.first).toBe(false)
  })

  it('streamError fails every still-live lane', () => {
    const partial = arenaReducer(start, { type: 'result', result: result({}) })
    const s = arenaReducer(partial, { type: 'streamError' })
    expect(s.lanes.CHATGPT.status).toBe('error')
    expect(s.lanes.CLAUDE.status).toBe('done') // already finished, untouched
    expect(s.done).toBe(true)
  })

  it('done marks the run complete', () => {
    expect(arenaReducer(start, { type: 'done' }).done).toBe(true)
  })
})
