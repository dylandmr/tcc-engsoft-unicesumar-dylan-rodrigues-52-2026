import { describe, expect, it } from 'vitest'
import {
  countTokens,
  formatDelta,
  formatLatency,
  formatTokensPerSecond,
  relativeTime,
} from './format'

describe('formatLatency', () => {
  it('renders ms as seconds with two decimals', () => {
    expect(formatLatency(1840)).toBe('1.84s')
    expect(formatLatency(970)).toBe('0.97s')
  })
})

describe('formatDelta', () => {
  it('renders the gap to the winner as a signed seconds readout', () => {
    expect(formatDelta(420)).toBe('+0.42s')
    expect(formatDelta(0)).toBe('+0.00s')
  })
})

describe('formatTokensPerSecond', () => {
  it('renders throughput with one decimal', () => {
    expect(formatTokensPerSecond(38.06)).toBe('38.1 tok/s')
    expect(formatTokensPerSecond(50)).toBe('50.0 tok/s')
  })
})

describe('countTokens', () => {
  it('counts whitespace-delimited words', () => {
    expect(countTokens('one two three')).toBe(3)
  })
  it('returns 0 for blank text', () => {
    expect(countTokens('   ')).toBe(0)
  })
})

describe('relativeTime', () => {
  const base = new Date('2026-06-30T12:00:00Z').getTime()
  const ago = (ms: number) => new Date(base - ms).toISOString()

  it('handles all coarse buckets', () => {
    expect(relativeTime(ago(10_000), base)).toBe('agora')
    expect(relativeTime(ago(5 * 60_000), base)).toBe('há 5 min')
    expect(relativeTime(ago(60 * 60_000), base)).toBe('há 1 hora')
    expect(relativeTime(ago(3 * 60 * 60_000), base)).toBe('há 3 horas')
    expect(relativeTime(ago(24 * 60 * 60_000), base)).toBe('ontem')
    expect(relativeTime(ago(3 * 24 * 60 * 60_000), base)).toBe('há 3 dias')
  })

  it('defaults the reference time to now', () => {
    expect(relativeTime(new Date().toISOString())).toBe('agora')
  })
})
