import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay } from 'msw'
import { http, HttpResponse, server } from '../../testing/server'
import { renderApp } from '../../testing/render'
import type { ProviderStats } from '../types'

/** Seed the history list handler with the given comparisons. */
function seedHistory() {
  server.use(
    http.get('/api/comparisons', () =>
      HttpResponse.json({
        comparisons: [
          {
            id: 'c1',
            prompt: 'Primeiro prompt',
            providers: ['CLAUDE'],
            createdAt: new Date(Date.now() - 60_000).toISOString(),
          },
          {
            id: 'c2',
            prompt: 'Segundo prompt',
            providers: ['GEMINI'],
            createdAt: new Date(Date.now() - 120_000).toISOString(),
          },
        ],
      }),
    ),
  )
}

/** Seed the FR-023 aggregates handler with the given entries. */
function seedStats(stats: ProviderStats[]) {
  server.use(
    http.get('/api/comparisons/stats', () => HttpResponse.json({ stats })),
  )
}

/** Full telemetry, plural runs, singular erro/timeout, zero empties hidden. */
const geminiStats: ProviderStats = {
  provider: 'GEMINI',
  runs: 9,
  successes: 7,
  empties: 0,
  errors: 1,
  timeouts: 1,
  telemetryRuns: 6,
  avgResponseTimeMs: 2310,
  avgFirstTokenMs: 820,
  avgTokensPerSecond: 41.2,
}

/** No telemetry at all — every average is null — and plural outcome labels. */
const chatgptStats: ProviderStats = {
  provider: 'CHATGPT',
  runs: 6,
  successes: 0,
  empties: 2,
  errors: 2,
  timeouts: 2,
  telemetryRuns: 0,
  avgResponseTimeMs: null,
  avgFirstTokenMs: null,
  avgTokensPerSecond: null,
}

/** A single run: singular labels, partial telemetry, zero errors/timeouts. */
const claudeStats: ProviderStats = {
  provider: 'CLAUDE',
  runs: 1,
  successes: 0,
  empties: 1,
  errors: 0,
  timeouts: 0,
  telemetryRuns: 1,
  avgResponseTimeMs: 1500,
  avgFirstTokenMs: null,
  avgTokensPerSecond: null,
}

describe('HistoryPage', () => {
  it('shows an empty state when there are no comparisons', async () => {
    renderApp({ route: '/history' })
    expect(
      await screen.findByText(/Nenhuma comparação ainda/),
    ).toBeInTheDocument()
  })

  it('shows an error state when history fails to load', async () => {
    server.use(
      http.get('/api/comparisons', () =>
        HttpResponse.json({ error: 'boom' }, { status: 500 }),
      ),
    )
    renderApp({ route: '/history' })
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Não foi possível carregar o histórico/,
    )
  })

  it('lists past comparisons and reopens one in the arena', async () => {
    server.use(
      http.get('/api/comparisons', () =>
        HttpResponse.json({
          comparisons: [
            {
              id: 'c9',
              prompt: 'My old prompt',
              providers: ['CLAUDE', 'CHATGPT'],
              createdAt: new Date(Date.now() - 120_000).toISOString(),
            },
          ],
        }),
      ),
    )
    renderApp({ route: '/history' })
    const row = await screen.findByText('My old prompt')
    expect(screen.getByText('há 2 min')).toBeInTheDocument()
    await userEvent.click(row)
    await waitFor(() =>
      expect(
        screen.getByRole('region', { name: 'Claude' }),
      ).toBeInTheDocument(),
    )
  })

  it('signs out from history', async () => {
    renderApp({ route: '/history' })
    await screen.findByText(/Nenhuma comparação ainda/)
    await userEvent.click(screen.getByRole('button', { name: 'sair' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Usuário')).toBeInTheDocument(),
    )
  })

  it('hides the clear-history action while empty (FR-022)', async () => {
    renderApp({ route: '/history' })
    await screen.findByText(/Nenhuma comparação ainda/)
    expect(
      screen.queryByRole('button', { name: 'limpar histórico' }),
    ).not.toBeInTheDocument()
  })

  it('clears the whole history after an inline confirmation (FR-022)', async () => {
    seedHistory()
    server.use(
      http.delete(
        '/api/comparisons',
        () => new HttpResponse(null, { status: 204 }),
      ),
    )
    renderApp({ route: '/history' })
    await screen.findByText('Primeiro prompt')

    await userEvent.click(
      screen.getByRole('button', { name: 'limpar histórico' }),
    )
    expect(screen.getByText('apagar todas as comparações?')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'confirmar' }))

    expect(
      await screen.findByText(/Nenhuma comparação ainda/),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'limpar histórico' }),
    ).not.toBeInTheDocument()
  })

  it('cancels clearing the history', async () => {
    seedHistory()
    renderApp({ route: '/history' })
    await screen.findByText('Primeiro prompt')

    await userEvent.click(
      screen.getByRole('button', { name: 'limpar histórico' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'cancelar' }))

    expect(
      screen.queryByText('apagar todas as comparações?'),
    ).not.toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'limpar histórico' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Primeiro prompt')).toBeInTheDocument()
  })

  it('surfaces an inline error when clearing fails, keeping the list', async () => {
    seedHistory()
    server.use(
      http.delete('/api/comparisons', () =>
        HttpResponse.json({ error: 'boom' }, { status: 500 }),
      ),
    )
    renderApp({ route: '/history' })
    await screen.findByText('Primeiro prompt')

    await userEvent.click(
      screen.getByRole('button', { name: 'limpar histórico' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'confirmar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Não foi possível apagar\. Tente novamente\./,
    )
    expect(screen.getByText('Primeiro prompt')).toBeInTheDocument()
    expect(screen.getByText('Segundo prompt')).toBeInTheDocument()
  })

  it('deletes a single row after an inline confirmation, without navigating (FR-022)', async () => {
    seedHistory()
    server.use(
      http.delete(
        '/api/comparisons/c1',
        () => new HttpResponse(null, { status: 204 }),
      ),
    )
    renderApp({ route: '/history' })
    await screen.findByText('Primeiro prompt')

    await userEvent.click(
      screen.getByRole('button', { name: 'apagar "Primeiro prompt"' }),
    )
    // Still on the history page — the delete affordance never opens the row.
    expect(screen.getByText('Comparações anteriores')).toBeInTheDocument()
    await userEvent.click(
      screen.getByRole('button', {
        name: 'confirmar exclusão de "Primeiro prompt"',
      }),
    )

    await waitFor(() =>
      expect(screen.queryByText('Primeiro prompt')).not.toBeInTheDocument(),
    )
    expect(screen.getByText('Segundo prompt')).toBeInTheDocument()
    expect(screen.getByText('Comparações anteriores')).toBeInTheDocument()
  })

  it('cancels a single row deletion', async () => {
    seedHistory()
    renderApp({ route: '/history' })
    await screen.findByText('Primeiro prompt')

    await userEvent.click(
      screen.getByRole('button', { name: 'apagar "Primeiro prompt"' }),
    )
    await userEvent.click(
      screen.getByRole('button', {
        name: 'cancelar exclusão de "Primeiro prompt"',
      }),
    )

    expect(screen.getByText('Primeiro prompt')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'apagar "Primeiro prompt"' }),
    ).toBeInTheDocument()
  })

  it('surfaces an inline error when a row deletion fails, keeping the row', async () => {
    seedHistory()
    server.use(
      http.delete('/api/comparisons/c1', () =>
        HttpResponse.json({ error: 'boom' }, { status: 500 }),
      ),
    )
    renderApp({ route: '/history' })
    await screen.findByText('Primeiro prompt')

    await userEvent.click(
      screen.getByRole('button', { name: 'apagar "Primeiro prompt"' }),
    )
    await userEvent.click(
      screen.getByRole('button', {
        name: 'confirmar exclusão de "Primeiro prompt"',
      }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Não foi possível apagar\. Tente novamente\./,
    )
    expect(screen.getByText('Primeiro prompt')).toBeInTheDocument()
  })

  it('shows no statistics for a user with no runs (FR-023)', async () => {
    renderApp({ route: '/history' })
    await screen.findByText(/Nenhuma comparação ainda/)
    expect(screen.queryByText('ESTATÍSTICAS')).not.toBeInTheDocument()
  })

  it('omits the statistics strip while the aggregates are still loading', async () => {
    seedHistory()
    server.use(
      http.get('/api/comparisons/stats', async () => {
        await delay('infinite')
        return HttpResponse.json({ stats: [] })
      }),
    )
    renderApp({ route: '/history' })
    await screen.findByText('Primeiro prompt')
    expect(screen.queryByText('ESTATÍSTICAS')).not.toBeInTheDocument()
  })

  it('renders the list normally when the statistics fetch fails', async () => {
    seedHistory()
    server.use(
      http.get('/api/comparisons/stats', () =>
        HttpResponse.json({ error: 'boom' }, { status: 500 }),
      ),
    )
    renderApp({ route: '/history' })
    await screen.findByText('Primeiro prompt')
    expect(screen.getByText('Segundo prompt')).toBeInTheDocument()
    expect(screen.queryByText('ESTATÍSTICAS')).not.toBeInTheDocument()
  })

  it('renders one aggregate card per provider, in API order, with counts, averages and honest basis (FR-023)', async () => {
    seedHistory()
    seedStats([geminiStats, chatgptStats, claudeStats])
    renderApp({ route: '/history' })
    expect(await screen.findByText('ESTATÍSTICAS')).toBeInTheDocument()
    // The strip sits alongside the list — it never displaces it.
    expect(await screen.findByText('Primeiro prompt')).toBeInTheDocument()

    const cards = screen.getAllByRole('group')
    expect(cards.map((card) => card.getAttribute('aria-label'))).toEqual([
      'estatísticas de Gemini',
      'estatísticas de ChatGPT',
      'estatísticas de Claude',
    ])

    const gemini = within(cards[0])
    expect(gemini.getByText('9 corridas')).toBeInTheDocument()
    expect(gemini.getByText('7 ok · 1 erro · 1 timeout')).toBeInTheDocument()
    expect(gemini.getByText('2.31s')).toBeInTheDocument()
    expect(gemini.getByText('0.82s')).toBeInTheDocument()
    expect(gemini.getByText('41.2 tok/s')).toBeInTheDocument()
    expect(
      gemini.getByText('telemetria em 6 de 9 execuções'),
    ).toBeInTheDocument()

    const chatgpt = within(cards[1])
    expect(chatgpt.getByText('6 corridas')).toBeInTheDocument()
    expect(
      chatgpt.getByText('0 ok · 2 vazias · 2 erros · 2 timeouts'),
    ).toBeInTheDocument()
    expect(chatgpt.getAllByText('—')).toHaveLength(3)
    expect(
      chatgpt.getByText('telemetria em 0 de 6 execuções'),
    ).toBeInTheDocument()

    const claude = within(cards[2])
    expect(claude.getByText('1 corrida')).toBeInTheDocument()
    expect(claude.getByText('0 ok · 1 vazia')).toBeInTheDocument()
    expect(claude.getByText('1.50s')).toBeInTheDocument()
    expect(claude.getAllByText('—')).toHaveLength(2)
    expect(
      claude.getByText('telemetria em 1 de 1 execuções'),
    ).toBeInTheDocument()
  })

  it('refreshes the aggregates after deleting an entry (FR-023)', async () => {
    seedHistory()
    let deleted = false
    server.use(
      http.delete('/api/comparisons/c1', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
      http.get('/api/comparisons/stats', () =>
        HttpResponse.json({
          stats: [
            deleted ? { ...geminiStats, runs: 8, successes: 6 } : geminiStats,
          ],
        }),
      ),
    )
    renderApp({ route: '/history' })
    expect(await screen.findByText('9 corridas')).toBeInTheDocument()

    await userEvent.click(
      screen.getByRole('button', { name: 'apagar "Primeiro prompt"' }),
    )
    await userEvent.click(
      screen.getByRole('button', {
        name: 'confirmar exclusão de "Primeiro prompt"',
      }),
    )

    expect(await screen.findByText('8 corridas')).toBeInTheDocument()
    expect(screen.queryByText('9 corridas')).not.toBeInTheDocument()
    expect(screen.getByText('Segundo prompt')).toBeInTheDocument()
  })

  it('drops the statistics strip after clearing the history (FR-023)', async () => {
    seedHistory()
    let cleared = false
    server.use(
      http.delete('/api/comparisons', () => {
        cleared = true
        return new HttpResponse(null, { status: 204 })
      }),
      http.get('/api/comparisons/stats', () =>
        HttpResponse.json({ stats: cleared ? [] : [geminiStats] }),
      ),
    )
    renderApp({ route: '/history' })
    await screen.findByText('ESTATÍSTICAS')

    await userEvent.click(
      screen.getByRole('button', { name: 'limpar histórico' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'confirmar' }))

    expect(
      await screen.findByText(/Nenhuma comparação ainda/),
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByText('ESTATÍSTICAS')).not.toBeInTheDocument(),
    )
  })
})
