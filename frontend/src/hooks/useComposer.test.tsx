import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { FormEvent } from 'react'
import type { ProviderCatalogEntry } from '../types'
import { LAUNCH_HOLD_MS, useComposer } from './useComposer'
import { createComparison, getProviders } from '../api/client'

const { navigate } = vi.hoisted(() => ({ navigate: vi.fn() }))

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => navigate,
}))

vi.mock('../api/client', () => ({
  getProviders: vi.fn(),
  createComparison: vi.fn(),
}))

const entry = (
  over: Partial<ProviderCatalogEntry> & Pick<ProviderCatalogEntry, 'provider'>,
): ProviderCatalogEntry => ({
  configured: true,
  defaultModel: 'claude-3-5-sonnet-latest',
  models: ['claude-3-5-haiku-latest', 'claude-3-5-sonnet-latest'],
  source: 'curated',
  ...over,
})

const CATALOG: ProviderCatalogEntry[] = [
  entry({
    provider: 'GEMINI',
    defaultModel: 'gemini-2.5-flash',
    models: ['gemini-2.0-flash', 'gemini-2.5-flash', 'gemini-2.5-pro'],
    source: 'live',
  }),
  entry({
    provider: 'CHATGPT',
    defaultModel: 'gpt-4o-mini',
    models: ['gpt-4o', 'gpt-4o-mini'],
  }),
  entry({ provider: 'CLAUDE' }),
  entry({
    provider: 'GROK',
    configured: false,
    defaultModel: 'grok-2-latest',
    models: ['grok-2-latest'],
  }),
  entry({
    provider: 'DEEPSEEK',
    defaultModel: 'deepseek-chat',
    models: ['deepseek-chat'],
  }),
]

const submitEvent = () => ({ preventDefault: vi.fn() }) as unknown as FormEvent

const stubMotionPreference = (matches: boolean) =>
  vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches }))

beforeEach(() => {
  vi.mocked(getProviders).mockResolvedValue(CATALOG)
  vi.mocked(createComparison).mockResolvedValue({
    comparisonId: 'c_test',
    providers: ['CLAUDE'],
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.clearAllMocks()
  vi.useRealTimers()
})

describe('useComposer.toggle', () => {
  it('ignores a fifth selection once the limit is reached', () => {
    const { result } = renderHook(() => useComposer())

    act(() => {
      result.current.toggle('GEMINI')
      result.current.toggle('CHATGPT')
      result.current.toggle('CLAUDE')
      result.current.toggle('GROK')
    })
    expect(result.current.selected).toHaveLength(4)
    expect(result.current.full).toBe(true)

    act(() => result.current.toggle('DEEPSEEK'))
    expect(result.current.selected).toHaveLength(4)
    expect(result.current.selected).not.toContain('DEEPSEEK')
  })

  it('forgets an explicit model pick when the provider is disarmed', async () => {
    const { result } = renderHook(() => useComposer())
    await waitFor(() =>
      expect(result.current.catalog.GROK.configured).toBe(false),
    )

    act(() => result.current.toggle('CLAUDE'))
    expect(result.current.modelFor('CLAUDE')).toBe('claude-3-5-sonnet-latest')

    act(() => result.current.setModel('CLAUDE', 'claude-3-5-haiku-latest'))
    expect(result.current.modelFor('CLAUDE')).toBe('claude-3-5-haiku-latest')

    act(() => result.current.toggle('CLAUDE')) // disarm
    act(() => result.current.toggle('CLAUDE')) // re-arm
    expect(result.current.modelFor('CLAUDE')).toBe('claude-3-5-sonnet-latest')
  })
})

describe('useComposer catalog', () => {
  it('starts from the static fallback and adopts the fetched catalog', async () => {
    let resolve!: (v: ProviderCatalogEntry[]) => void
    vi.mocked(getProviders).mockReturnValue(
      new Promise((r) => {
        resolve = r
      }),
    )
    const { result } = renderHook(() => useComposer())

    // Fallback while the request is in flight: default-only, assumed configured.
    expect(result.current.modelFor('GEMINI')).toBe('gemini-2.5-flash')
    expect(result.current.catalog.GEMINI.models).toEqual(['gemini-2.5-flash'])
    expect(result.current.catalog.GROK.configured).toBe(true)

    await act(async () => resolve(CATALOG))
    expect(result.current.catalog.GEMINI.models).toContain('gemini-2.5-pro')
    expect(result.current.catalog.GROK.configured).toBe(false)
  })

  it('re-anchors picks the fresh catalog no longer lists, keeping valid ones', async () => {
    let resolve!: (v: ProviderCatalogEntry[]) => void
    vi.mocked(getProviders).mockReturnValue(
      new Promise((r) => {
        resolve = r
      }),
    )
    const { result } = renderHook(() => useComposer())

    // Picks made against the fallback catalog, before the fetch lands.
    act(() => {
      result.current.toggle('GEMINI')
      result.current.toggle('CLAUDE')
    })
    act(() => {
      result.current.setModel('GEMINI', 'gemini-2.5-flash')
      result.current.setModel('CLAUDE', 'claude-3-5-haiku-latest')
    })

    // The fresh catalog drops GEMINI's picked model but keeps CLAUDE's.
    await act(async () =>
      resolve([
        entry({
          provider: 'GEMINI',
          defaultModel: 'gemini-3-flash',
          models: ['gemini-3-flash'],
        }),
        entry({ provider: 'CLAUDE' }),
      ]),
    )
    expect(result.current.modelFor('GEMINI')).toBe('gemini-3-flash')
    expect(result.current.modelFor('CLAUDE')).toBe('claude-3-5-haiku-latest')
  })

  it('keeps composing on the fallback when the catalog fetch fails', async () => {
    vi.mocked(getProviders).mockRejectedValue(new Error('down'))
    const { result } = renderHook(() => useComposer())
    // Flush the rejection; the fallback must remain untouched.
    await act(async () => {})
    expect(result.current.modelFor('CHATGPT')).toBe('gpt-4o-mini')
    expect(result.current.catalog.CHATGPT.models).toEqual(['gpt-4o-mini'])
  })
})

describe('useComposer.run', () => {
  it('sends every armed provider’s current model and holds the launch overlay', async () => {
    stubMotionPreference(false)
    vi.useFakeTimers()
    const { result } = renderHook(() => useComposer())
    await act(async () => {}) // catalog settles

    act(() => {
      result.current.setPrompt('Explique buracos negros.')
      result.current.toggle('CLAUDE')
      result.current.toggle('GEMINI')
    })
    act(() => result.current.setModel('GEMINI', 'gemini-2.5-pro'))

    await act(async () => result.current.run(submitEvent()))
    expect(createComparison).toHaveBeenCalledWith(
      'Explique buracos negros.',
      ['CLAUDE', 'GEMINI'],
      { CLAUDE: 'claude-3-5-sonnet-latest', GEMINI: 'gemini-2.5-pro' },
    )
    expect(result.current.launching).toBe(true)
    expect(navigate).not.toHaveBeenCalled()

    act(() => vi.advanceTimersByTime(LAUNCH_HOLD_MS))
    expect(navigate).toHaveBeenCalledWith('/results/c_test', {
      state: {
        providers: ['CLAUDE'],
        prompt: 'Explique buracos negros.',
        models: {
          CLAUDE: 'claude-3-5-sonnet-latest',
          GEMINI: 'gemini-2.5-pro',
        },
      },
    })
  })

  it('skips the launch hold under prefers-reduced-motion', async () => {
    stubMotionPreference(true)
    const { result } = renderHook(() => useComposer())
    await act(async () => {})

    act(() => {
      result.current.setPrompt('p')
      result.current.toggle('CLAUDE')
    })
    await act(async () => result.current.run(submitEvent()))
    expect(navigate).toHaveBeenCalledTimes(1)
  })

  it('surfaces validation errors without dispatching', async () => {
    const { result } = renderHook(() => useComposer())
    await act(async () => result.current.run(submitEvent()))
    expect(result.current.error).toMatch(/Digite um prompt/)
    expect(createComparison).not.toHaveBeenCalled()
    expect(result.current.launching).toBe(false)
  })

  it('cancels the launch overlay and reports when the run cannot start', async () => {
    vi.mocked(createComparison).mockRejectedValue(new Error('boom'))
    const { result } = renderHook(() => useComposer())
    await act(async () => {})

    act(() => {
      result.current.setPrompt('p')
      result.current.toggle('CLAUDE')
    })
    await act(async () => result.current.run(submitEvent()))
    expect(result.current.error).toMatch(/Não foi possível iniciar/)
    expect(result.current.launching).toBe(false)
    expect(result.current.submitting).toBe(false)
    expect(navigate).not.toHaveBeenCalled()
  })

  it('cancels the pending launch navigation on unmount', async () => {
    stubMotionPreference(false)
    vi.useFakeTimers()
    const { result, unmount } = renderHook(() => useComposer())
    await act(async () => {})

    act(() => {
      result.current.setPrompt('p')
      result.current.toggle('CLAUDE')
    })
    await act(async () => result.current.run(submitEvent()))
    expect(result.current.launching).toBe(true)

    unmount()
    act(() => vi.advanceTimersByTime(LAUNCH_HOLD_MS))
    expect(navigate).not.toHaveBeenCalled()
  })
})
