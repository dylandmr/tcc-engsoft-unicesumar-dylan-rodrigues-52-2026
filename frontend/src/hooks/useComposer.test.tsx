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
  models: ['claude-3-5-haiku-latest', 'claude-3-5-sonnet-latest'],
  ...over,
})

/** Default catalog: GROK is unconfigured (empty list), the rest armable. */
const CATALOG: ProviderCatalogEntry[] = [
  entry({
    provider: 'GEMINI',
    models: ['gemini-2.0-flash', 'gemini-2.5-flash', 'gemini-2.5-pro'],
  }),
  entry({ provider: 'CHATGPT', models: ['gpt-4o', 'gpt-4o-mini'] }),
  entry({ provider: 'CLAUDE' }),
  entry({ provider: 'GROK', configured: false, models: [] }),
  entry({ provider: 'DEEPSEEK', models: ['deepseek-chat'] }),
]

/** Catalog where all five providers are armable (for the 4-limit rule). */
const ALL_ARMABLE: ProviderCatalogEntry[] = CATALOG.map((e) =>
  e.provider === 'GROK'
    ? entry({ provider: 'GROK', models: ['grok-2-latest'] })
    : e,
)

const submitEvent = () => ({ preventDefault: vi.fn() }) as unknown as FormEvent

const stubMotionPreference = (matches: boolean) =>
  vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches }))

/** Render the hook and wait for the default catalog to become ready. */
async function renderReady() {
  const view = renderHook(() => useComposer())
  await waitFor(() => expect(view.result.current.catalogState).toBe('ready'))
  return view
}

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
  it('does not arm any provider while the catalog is still loading', async () => {
    let resolve!: (v: ProviderCatalogEntry[]) => void
    vi.mocked(getProviders).mockReturnValue(
      new Promise((r) => {
        resolve = r
      }),
    )
    const { result } = renderHook(() => useComposer())
    expect(result.current.catalogState).toBe('loading')

    act(() => result.current.toggle('GEMINI'))
    expect(result.current.selected).toEqual([])

    await act(async () => resolve(CATALOG))
    act(() => result.current.toggle('GEMINI'))
    expect(result.current.selected).toEqual(['GEMINI'])
  })

  it('refuses to arm a provider whose catalog entry has no models', async () => {
    const { result } = await renderReady()
    act(() => result.current.toggle('GROK'))
    expect(result.current.selected).toEqual([])
    expect(result.current.armable('GROK')).toBe(false)
  })

  it('ignores a fifth selection once the limit is reached', async () => {
    vi.mocked(getProviders).mockResolvedValue(ALL_ARMABLE)
    const { result } = await renderReady()

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

  it('arms with no chosen model and forgets the pick when disarmed', async () => {
    const { result } = await renderReady()

    act(() => result.current.toggle('CLAUDE'))
    expect(result.current.modelFor('CLAUDE')).toBe('')

    act(() => result.current.setModel('CLAUDE', 'claude-3-5-haiku-latest'))
    expect(result.current.modelFor('CLAUDE')).toBe('claude-3-5-haiku-latest')

    act(() => result.current.toggle('CLAUDE')) // disarm
    act(() => result.current.toggle('CLAUDE')) // re-arm
    expect(result.current.modelFor('CLAUDE')).toBe('')
  })
})

describe('useComposer catalog', () => {
  it('reports armability and unavailability hints from the ready catalog', async () => {
    const { result } = await renderReady()
    expect(result.current.armable('GEMINI')).toBe(true)
    expect(result.current.unavailableHint('GEMINI')).toBeNull()
    // Unconfigured providers have empty model lists.
    expect(result.current.unavailableHint('GROK')).toBe('não configurado')
  })

  it('hints "modelos indisponíveis" for a configured provider whose list fetch failed', async () => {
    vi.mocked(getProviders).mockResolvedValue([
      ...CATALOG.filter((e) => e.provider !== 'DEEPSEEK'),
      entry({ provider: 'DEEPSEEK', models: [] }),
    ])
    const { result } = await renderReady()
    expect(result.current.armable('DEEPSEEK')).toBe(false)
    expect(result.current.unavailableHint('DEEPSEEK')).toBe(
      'modelos indisponíveis',
    )
  })

  it('flags a failed catalog fetch and recovers via retryCatalog', async () => {
    vi.mocked(getProviders).mockRejectedValue(new Error('down'))
    const { result } = renderHook(() => useComposer())
    await waitFor(() => expect(result.current.catalogState).toBe('error'))
    // Nothing is armable and no hint is offered while the catalog is absent.
    expect(result.current.armable('GEMINI')).toBe(false)
    expect(result.current.unavailableHint('GEMINI')).toBeNull()

    vi.mocked(getProviders).mockResolvedValue(CATALOG)
    act(() => result.current.retryCatalog())
    expect(result.current.catalogState).toBe('loading')
    await waitFor(() => expect(result.current.catalogState).toBe('ready'))
    expect(result.current.armable('GEMINI')).toBe(true)
  })

  it('disarms providers and drops picks the refetched catalog no longer offers', async () => {
    const { result } = await renderReady()

    act(() => {
      result.current.toggle('GEMINI')
      result.current.toggle('CHATGPT')
      result.current.toggle('CLAUDE')
      result.current.toggle('DEEPSEEK')
    })
    act(() => {
      result.current.setModel('GEMINI', 'gemini-2.5-pro')
      result.current.setModel('CHATGPT', 'gpt-4o')
      result.current.setModel('CLAUDE', 'claude-3-5-haiku-latest')
      result.current.setModel('DEEPSEEK', 'deepseek-chat')
    })

    // The refetch keeps CHATGPT intact, drops GEMINI's picked model, empties
    // CLAUDE's list and omits DEEPSEEK entirely.
    vi.mocked(getProviders).mockResolvedValue([
      entry({ provider: 'GEMINI', models: ['gemini-2.0-flash'] }),
      entry({ provider: 'CHATGPT', models: ['gpt-4o', 'gpt-4o-mini'] }),
      entry({ provider: 'CLAUDE', models: [] }),
    ])
    await act(async () => result.current.retryCatalog())

    // GEMINI stays armed but its stale pick is cleared; CHATGPT keeps its
    // pick; CLAUDE and DEEPSEEK are disarmed and hinted unavailable.
    expect(result.current.selected).toEqual(['GEMINI', 'CHATGPT'])
    expect(result.current.modelFor('GEMINI')).toBe('')
    expect(result.current.modelFor('CHATGPT')).toBe('gpt-4o')
    expect(result.current.modelFor('CLAUDE')).toBe('')
    expect(result.current.unavailableHint('CLAUDE')).toBe(
      'modelos indisponíveis',
    )
    expect(result.current.unavailableHint('DEEPSEEK')).toBe('não configurado')
  })
})

describe('useComposer.run', () => {
  it('blocks submission while an armed provider has no chosen model', async () => {
    const { result } = await renderReady()
    act(() => {
      result.current.setPrompt('p')
      result.current.toggle('CLAUDE')
      result.current.toggle('GEMINI')
    })
    act(() => result.current.setModel('CLAUDE', 'claude-3-5-sonnet-latest'))

    await act(async () => result.current.run(submitEvent()))
    expect(result.current.error).toBe(
      'Escolha a versão de cada modelo selecionado.',
    )
    expect(createComparison).not.toHaveBeenCalled()
    expect(result.current.launching).toBe(false)
  })

  it('sends the explicit picks and holds the launch overlay', async () => {
    stubMotionPreference(false)
    const { result } = await renderReady()
    vi.useFakeTimers()

    act(() => {
      result.current.setPrompt('Explique buracos negros.')
      result.current.toggle('CLAUDE')
      result.current.toggle('GEMINI')
    })
    act(() => {
      result.current.setModel('CLAUDE', 'claude-3-5-sonnet-latest')
      result.current.setModel('GEMINI', 'gemini-2.5-pro')
    })

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
    const { result } = await renderReady()

    act(() => {
      result.current.setPrompt('p')
      result.current.toggle('CLAUDE')
    })
    act(() => result.current.setModel('CLAUDE', 'claude-3-5-sonnet-latest'))
    await act(async () => result.current.run(submitEvent()))
    expect(navigate).toHaveBeenCalledTimes(1)
  })

  it('surfaces validation errors without dispatching', async () => {
    const { result } = await renderReady()
    await act(async () => result.current.run(submitEvent()))
    expect(result.current.error).toMatch(/Digite um prompt/)
    expect(createComparison).not.toHaveBeenCalled()
    expect(result.current.launching).toBe(false)
  })

  it('cancels the launch overlay and reports when the run cannot start', async () => {
    vi.mocked(createComparison).mockRejectedValue(new Error('boom'))
    const { result } = await renderReady()

    act(() => {
      result.current.setPrompt('p')
      result.current.toggle('CLAUDE')
    })
    act(() => result.current.setModel('CLAUDE', 'claude-3-5-sonnet-latest'))
    await act(async () => result.current.run(submitEvent()))
    expect(result.current.error).toMatch(/Não foi possível iniciar/)
    expect(result.current.launching).toBe(false)
    expect(result.current.submitting).toBe(false)
    expect(navigate).not.toHaveBeenCalled()
  })

  it('cancels the pending launch navigation on unmount', async () => {
    stubMotionPreference(false)
    const { result, unmount } = await renderReady()
    vi.useFakeTimers()

    act(() => {
      result.current.setPrompt('p')
      result.current.toggle('CLAUDE')
    })
    act(() => result.current.setModel('CLAUDE', 'claude-3-5-sonnet-latest'))
    await act(async () => result.current.run(submitEvent()))
    expect(result.current.launching).toBe(true)

    unmount()
    act(() => vi.advanceTimersByTime(LAUNCH_HOLD_MS))
    expect(navigate).not.toHaveBeenCalled()
  })
})
