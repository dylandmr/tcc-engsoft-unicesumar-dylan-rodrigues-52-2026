import { describe, expect, it } from 'vitest'
import { countTokens, formatLatency, relativeTime } from './format'

describe('formatLatency', () => {
  it('renders ms as seconds with two decimals', () => {
    expect(formatLatency(1840)).toBe('1.84s')
    expect(formatLatency(970)).toBe('0.97s')
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
    expect(relativeTime(ago(10_000), base)).toBe('just now')
    expect(relativeTime(ago(5 * 60_000), base)).toBe('5 min ago')
    expect(relativeTime(ago(60 * 60_000), base)).toBe('1 hour ago')
    expect(relativeTime(ago(3 * 60 * 60_000), base)).toBe('3 hours ago')
    expect(relativeTime(ago(24 * 60 * 60_000), base)).toBe('yesterday')
    expect(relativeTime(ago(3 * 24 * 60 * 60_000), base)).toBe('3 days ago')
  })

  it('defaults the reference time to now', () => {
    expect(relativeTime(new Date().toISOString())).toBe('just now')
  })
})
