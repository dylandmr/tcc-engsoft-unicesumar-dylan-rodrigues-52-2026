import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, server } from '../../testing/server'
import { renderApp } from '../../testing/render'

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
})
