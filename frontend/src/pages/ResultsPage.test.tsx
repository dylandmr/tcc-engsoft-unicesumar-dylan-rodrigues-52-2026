import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { http, recordedAnalysis, server } from '../../testing/server'
import { sseResponse } from '../../testing/sse'
import { ResultsPage } from './ResultsPage'

/** Two successful lanes — enough for the key-differences section (FR-021). */
const twoSuccessResults = [
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
]

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

  it('shows the requested model from navigation state while nothing is reported', async () => {
    renderResults({
      providers: ['CLAUDE'],
      models: { CLAUDE: 'claude-3-5-sonnet-latest' },
    })
    const claude = screen.getByRole('region', { name: 'Claude' })
    expect(
      within(claude).getByText('claude-3-5-sonnet-latest'),
    ).toBeInTheDocument()
    // The default stream result reports no model — the requested one stays.
    await waitFor(() =>
      expect(screen.getByText('concluído · 1 modelos')).toBeInTheDocument(),
    )
    expect(
      within(claude).getByText('claude-3-5-sonnet-latest'),
    ).toBeInTheDocument()
  })

  it('lets the provider-reported model win over the requested one', async () => {
    server.use(
      http.get('/api/comparisons/:id/stream', () =>
        sseResponse([
          {
            event: 'result',
            data: {
              provider: 'CLAUDE',
              outcome: 'SUCCESS',
              responseText: 'oi',
              errorMessage: null,
              responseTimeMs: 900,
              model: 'claude-3-5-sonnet-20241022',
            },
          },
          { event: 'done', data: { comparisonId: 'c1', completed: 1 } },
        ]),
      ),
    )
    renderResults({
      providers: ['CLAUDE'],
      models: { CLAUDE: 'claude-3-5-sonnet-latest' },
    })
    const claude = screen.getByRole('region', { name: 'Claude' })
    await waitFor(() =>
      expect(
        within(claude).getByText('claude-3-5-sonnet-20241022'),
      ).toBeInTheDocument(),
    )
    expect(
      within(claude).queryByText('claude-3-5-sonnet-latest'),
    ).not.toBeInTheDocument()
  })

  it('pins the winner badge to the fastest responseTimeMs even when results arrive out of order (history replay)', async () => {
    // History replay re-emits persisted results in selection order — the
    // slower GEMINI arrives first here, but CLAUDE ran faster and must win.
    server.use(
      http.get('/api/comparisons/:id/stream', () =>
        sseResponse([
          ...twoSuccessResults,
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
    // Two successes surface the judge picker — let its catalog fetch settle.
    expect(
      await screen.findByRole('combobox', { name: 'Juíza' }),
    ).toBeInTheDocument()
  })

  it('replays a recorded analysis from the results stream without the picker', async () => {
    server.use(
      http.get('/api/comparisons/:id/stream', () =>
        sseResponse([
          ...twoSuccessResults,
          { event: 'analysis', data: recordedAnalysis },
          { event: 'done', data: { comparisonId: 'c1', completed: 2 } },
        ]),
      ),
    )
    renderResults({ providers: ['GEMINI', 'CLAUDE'] })
    expect(await screen.findByText('DIFERENÇAS-CHAVE')).toBeInTheDocument()
    expect(
      screen.getByText('juíza: Claude · claude-haiku-4-5'),
    ).toBeInTheDocument()
    expect(screen.getByText('Modelo A → Gemini')).toBeInTheDocument()
    expect(
      screen.queryByRole('combobox', { name: 'Juíza' }),
    ).not.toBeInTheDocument()
  })

  it('runs the judge end-to-end from the drawer picker', async () => {
    server.use(
      http.get('/api/comparisons/:id/stream', () =>
        sseResponse([
          ...twoSuccessResults,
          { event: 'done', data: { comparisonId: 'c1', completed: 2 } },
        ]),
      ),
      http.get('/api/comparisons/:id/analysis/stream', () =>
        sseResponse([
          { event: 'analysis-chunk', data: { delta: '## Cobertura' } },
          { event: 'analysis', data: recordedAnalysis },
          { event: 'done', data: { comparisonId: 'c1' } },
        ]),
      ),
    )
    renderResults({ providers: ['GEMINI', 'CLAUDE'] })
    await screen.findByText('DIFERENÇAS-CHAVE')
    await userEvent.selectOptions(
      await screen.findByRole('combobox', { name: 'Juíza' }),
      'CLAUDE',
    )
    await userEvent.click(
      screen.getByRole('combobox', { name: 'Modelo da juíza' }),
    )
    await userEvent.click(
      screen.getByRole('option', { name: 'claude-3-5-haiku-latest' }),
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'analisar diferenças' }),
    )
    expect(
      await screen.findByText('juíza: Claude · claude-haiku-4-5'),
    ).toBeInTheDocument()
    expect(screen.getByText('Modelo B → Claude')).toBeInTheDocument()
  })
})
