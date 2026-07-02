import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { AnalysisState } from '../../hooks/arenaReducer'
import type { RecordedAnalysis } from '../../types'
import { RaceSummary } from './RaceSummary'
import type {
  RaceSummary as RaceSummaryData,
  SummaryRow,
} from '../../lib/raceSummary'

const row = (over: Partial<SummaryRow>): SummaryRow => ({
  provider: 'GEMINI',
  kind: 'ranked',
  status: 'done',
  rank: 1,
  responseTimeMs: 1840,
  deltaMs: null,
  barFraction: 1,
  firstTokenMs: 400,
  outputTokens: 128,
  tokensPerSecond: 38.06,
  model: 'gemini-2.5-flash',
  chars: 42,
  errorMessage: null,
  ...over,
})

const summary = (over: Partial<RaceSummaryData>): RaceSummaryData => ({
  rows: [row({})],
  winner: 'GEMINI',
  hasData: true,
  ...over,
})

const recorded: RecordedAnalysis = {
  text: '**Modelo A** cita fontes.',
  provider: 'CLAUDE',
  model: 'claude-haiku-4-5',
  labels: { A: 'GEMINI', B: 'CLAUDE' },
}

function renderSummary(
  data: RaceSummaryData,
  analysis: AnalysisState = { phase: 'idle' },
) {
  return render(
    <RaceSummary summary={data} analysis={analysis} onAnalyze={() => {}} />,
  )
}

describe('RaceSummary ranked rows', () => {
  it('renders the full telemetry line for a ranked provider', () => {
    renderSummary(
      summary({
        rows: [
          row({}),
          row({
            provider: 'CLAUDE',
            rank: 2,
            responseTimeMs: 2260,
            deltaMs: 420,
            barFraction: 2260 / 2500,
            model: 'claude-sonnet-4-5',
            chars: 10,
          }),
        ],
      }),
      // Two successes surface the analysis section; keep it static here.
      { phase: 'streaming', text: '' },
    )
    expect(screen.getByText('RESUMO DA CORRIDA')).toBeInTheDocument()
    expect(screen.getByText('telemetria desta execução')).toBeInTheDocument()
    expect(screen.getByText('1º')).toBeInTheDocument()
    expect(screen.getByText('2º')).toBeInTheDocument()
    expect(screen.getByText('1.84s')).toBeInTheDocument()
    expect(screen.getByText('+0.42s')).toBeInTheDocument()
    expect(screen.getAllByText('0.40s')).toHaveLength(2) // 1º token column
    expect(screen.getAllByText('128')).toHaveLength(2)
    expect(screen.getAllByText('38.1 tok/s')).toHaveLength(2)
    expect(
      screen.getByText('gemini-2.5-flash · 42 caracteres'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('claude-sonnet-4-5 · 10 caracteres'),
    ).toBeInTheDocument()
  })

  it('renders "—" placeholders for missing telemetry (untimed responder)', () => {
    renderSummary(
      summary({
        rows: [
          row({
            kind: 'untimed',
            rank: null,
            responseTimeMs: null,
            barFraction: null,
            firstTokenMs: null,
            outputTokens: null,
            tokensPerSecond: null,
            model: null,
            chars: 0,
          }),
        ],
      }),
    )
    // rank, latency, Δ, 1º token, tokens, tok/s all fall back to "—".
    expect(screen.getAllByText('—')).toHaveLength(6)
    expect(screen.getByText('— · 0 caracteres')).toBeInTheDocument()
  })
})

describe('RaceSummary fault and disabled rows', () => {
  it('renders an error row with its tone and message, no metrics', () => {
    renderSummary(
      summary({
        rows: [
          row({}),
          row({
            provider: 'GROK',
            kind: 'fault',
            status: 'error',
            rank: null,
            errorMessage: 'rate_limited',
          }),
        ],
      }),
    )
    const fault = screen.getByText('erro · rate_limited')
    expect(fault.className).toContain('text-error')
  })

  it('renders a timeout row without a message as just its label', () => {
    renderSummary(
      summary({
        rows: [
          row({
            provider: 'CHATGPT',
            kind: 'fault',
            status: 'timeout',
            rank: null,
            errorMessage: null,
          }),
        ],
      }),
    )
    const fault = screen.getByText('tempo esgotado')
    expect(fault.className).toContain('text-timeout')
  })

  it('renders an unconfigured provider dimmed, without its raw error code', () => {
    renderSummary(
      summary({
        rows: [
          row({
            provider: 'DEEPSEEK',
            kind: 'disabled',
            status: 'disabled',
            rank: null,
            errorMessage: 'provider_not_configured',
          }),
        ],
      }),
    )
    expect(screen.getByText('não configurado')).toBeInTheDocument()
    expect(
      screen.queryByText(/provider_not_configured/),
    ).not.toBeInTheDocument()
  })
})

describe('RaceSummary collapse/expand', () => {
  it('collapses to a one-line winner readout and expands back', async () => {
    renderSummary(summary({}))
    const toggle = screen.getByRole('button', { name: 'recolher' })
    expect(toggle).toHaveAttribute('aria-expanded', 'true')

    await userEvent.click(toggle)
    expect(screen.getByText('1º Gemini · 1.84s')).toBeInTheDocument()
    expect(screen.queryByText('latência')).not.toBeInTheDocument()
    const expand = screen.getByRole('button', { name: 'expandir' })
    expect(expand).toHaveAttribute('aria-expanded', 'false')

    await userEvent.click(expand)
    expect(screen.getByText('latência')).toBeInTheDocument()
    expect(screen.queryByText('1º Gemini · 1.84s')).not.toBeInTheDocument()
  })

  it('collapses to the no-data line when there is no winner', async () => {
    renderSummary(summary({ rows: [], hasData: false }))
    await userEvent.click(screen.getByRole('button', { name: 'recolher' }))
    expect(screen.getByText('sem dados desta execução')).toBeInTheDocument()
  })
})

describe('RaceSummary without data', () => {
  it('shows a single muted line when no lane produced telemetry', () => {
    renderSummary(
      summary({
        rows: [
          row({
            provider: 'GEMINI',
            kind: 'fault',
            status: 'error',
            rank: null,
            errorMessage: 'boom',
          }),
        ],
        winner: null,
        hasData: false,
      }),
    )
    expect(screen.getByText('sem dados desta execução')).toBeInTheDocument()
    expect(screen.queryByText(/boom/)).not.toBeInTheDocument()
    expect(screen.queryByText('latência')).not.toBeInTheDocument()
  })
})

describe('RaceSummary diferenças-chave section (FR-021)', () => {
  const twoSuccesses = summary({
    rows: [
      row({}),
      row({ provider: 'CLAUDE', rank: 2, responseTimeMs: 2260, deltaMs: 420 }),
    ],
  })

  it('hides the section entirely with fewer than two successful lanes', () => {
    renderSummary(summary({}))
    expect(screen.queryByText('DIFERENÇAS-CHAVE')).not.toBeInTheDocument()
  })

  it('does not count faulted lanes as successes', () => {
    renderSummary(
      summary({
        rows: [
          row({}),
          row({
            provider: 'GROK',
            kind: 'fault',
            status: 'error',
            rank: null,
            errorMessage: 'rate_limited',
          }),
        ],
      }),
    )
    expect(screen.queryByText('DIFERENÇAS-CHAVE')).not.toBeInTheDocument()
  })

  it('shows the judge picker once two lanes succeeded and none is recorded', async () => {
    renderSummary(twoSuccesses)
    expect(screen.getByText('DIFERENÇAS-CHAVE')).toBeInTheDocument()
    // Let the picker's catalog fetch settle inside the test.
    expect(
      await screen.findByRole('combobox', { name: 'Juíza' }),
    ).toBeInTheDocument()
  })

  it('shows a replayed recorded analysis directly — no judge picker', () => {
    renderSummary(twoSuccesses, { phase: 'done', analysis: recorded })
    expect(
      screen.getByText('juíza: Claude · claude-haiku-4-5'),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('combobox', { name: 'Juíza' }),
    ).not.toBeInTheDocument()
  })
})
