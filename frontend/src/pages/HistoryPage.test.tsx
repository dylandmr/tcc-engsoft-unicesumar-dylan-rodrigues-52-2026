import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, server } from '../../testing/server'
import { renderApp } from '../../testing/render'

describe('HistoryPage', () => {
  it('shows an empty state when there are no comparisons', async () => {
    renderApp({ route: '/history' })
    expect(
      await screen.findByText(/No comparisons yet/),
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
      /Could not load history/,
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
    expect(screen.getByText('2 min ago')).toBeInTheDocument()
    await userEvent.click(row)
    await waitFor(() =>
      expect(screen.getByRole('region', { name: 'Claude' })).toBeInTheDocument(),
    )
  })

  it('signs out from history', async () => {
    renderApp({ route: '/history' })
    await screen.findByText(/No comparisons yet/)
    await userEvent.click(screen.getByRole('button', { name: 'sign out' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Username')).toBeInTheDocument(),
    )
  })
})
