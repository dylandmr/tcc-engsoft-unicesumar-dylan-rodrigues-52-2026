import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { http, server } from '../../testing/server'
import { sseResponse } from '../../testing/sse'
import { ResultsPage } from './ResultsPage'

function renderResults(state: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/results/c1', state }]}>
      <Routes>
        <Route path="/results/:id" element={<ResultsPage />} />
        <Route path="/" element={<div>composer home</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ResultsPage', () => {
  it('redirects to the composer when navigated to without router state', () => {
    renderResults(null)
    expect(screen.getByText('composer home')).toBeInTheDocument()
  })

  it('renders one lane per provider and streams it to completion', async () => {
    renderResults({ providers: ['CLAUDE'] })
    expect(screen.getByRole('region', { name: 'Claude' })).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByText('concluído · 1 modelos')).toBeInTheDocument(),
    )
  })

  it('surfaces the "Resumo da corrida" drawer once the run completes', async () => {
    renderResults({ providers: ['CLAUDE'] })
    const drawer = await screen.findByRole('region', {
      name: 'Resumo da corrida',
    })
    expect(within(drawer).getByText('1º')).toBeInTheDocument()
    expect(within(drawer).getByText('1.84s')).toBeInTheDocument()
  })

  it('pins the winner badge to the fastest responseTimeMs even when results arrive out of order (history replay)', async () => {
    // History replay re-emits persisted results in selection order — the
    // slower GEMINI arrives first here, but CLAUDE ran faster and must win.
    server.use(
      http.get('/api/comparisons/:id/stream', () =>
        sseResponse([
          {
            event: 'result',
            data: {
              provider: 'GEMINI',
              outcome: 'SUCCESS',
              responseText: 'devagar',
              errorMessage: null,
              responseTimeMs: 2500,
            },
          },
          {
            event: 'result',
            data: {
              provider: 'CLAUDE',
              outcome: 'SUCCESS',
              responseText: 'rápido',
              errorMessage: null,
              responseTimeMs: 1200,
            },
          },
          { event: 'done', data: { comparisonId: 'c1', completed: 2 } },
        ]),
      ),
    )
    renderResults({ providers: ['GEMINI', 'CLAUDE'] })
    await waitFor(() =>
      expect(screen.getByText('concluído · 2 modelos')).toBeInTheDocument(),
    )
    const claude = screen.getByRole('region', { name: 'Claude' })
    expect(within(claude).getByText('primeiro a responder')).toBeInTheDocument()
    const gemini = screen.getByRole('region', { name: 'Gemini' })
    expect(
      within(gemini).queryByText('primeiro a responder'),
    ).not.toBeInTheDocument()
  })
})
