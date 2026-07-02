import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

describe('RaceSummary ranked rows', () => {
  it('renders the full telemetry line for a ranked provider', () => {
    render(
      <RaceSummary
        summary={summary({
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
        })}
      />,
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
    render(
      <RaceSummary
        summary={summary({
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
        })}
      />,
    )
    // rank, latency, Δ, 1º token, tokens, tok/s all fall back to "—".
    expect(screen.getAllByText('—')).toHaveLength(6)
    expect(screen.getByText('— · 0 caracteres')).toBeInTheDocument()
  })
})

describe('RaceSummary fault and disabled rows', () => {
  it('renders an error row with its tone and message, no metrics', () => {
    render(
      <RaceSummary
        summary={summary({
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
        })}
      />,
    )
    const fault = screen.getByText('erro · rate_limited')
    expect(fault.className).toContain('text-error')
  })

  it('renders a timeout row without a message as just its label', () => {
    render(
      <RaceSummary
        summary={summary({
          rows: [
            row({
              provider: 'CHATGPT',
              kind: 'fault',
              status: 'timeout',
              rank: null,
              errorMessage: null,
            }),
          ],
        })}
      />,
    )
    const fault = screen.getByText('tempo esgotado')
    expect(fault.className).toContain('text-timeout')
  })

  it('renders an unconfigured provider dimmed, without its raw error code', () => {
    render(
      <RaceSummary
        summary={summary({
          rows: [
            row({
              provider: 'DEEPSEEK',
              kind: 'disabled',
              status: 'disabled',
              rank: null,
              errorMessage: 'provider_not_configured',
            }),
          ],
        })}
      />,
    )
    expect(screen.getByText('não configurado')).toBeInTheDocument()
    expect(
      screen.queryByText(/provider_not_configured/),
    ).not.toBeInTheDocument()
  })
})

describe('RaceSummary collapse/expand', () => {
  it('collapses to a one-line winner readout and expands back', async () => {
    render(<RaceSummary summary={summary({})} />)
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
    render(<RaceSummary summary={summary({ rows: [], hasData: false })} />)
    await userEvent.click(screen.getByRole('button', { name: 'recolher' }))
    expect(screen.getByText('sem dados desta execução')).toBeInTheDocument()
  })
})

describe('RaceSummary without data', () => {
  it('shows a single muted line when no lane produced telemetry', () => {
    render(
      <RaceSummary
        summary={summary({
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
        })}
      />,
    )
    expect(screen.getByText('sem dados desta execução')).toBeInTheDocument()
    expect(screen.queryByText(/boom/)).not.toBeInTheDocument()
    expect(screen.queryByText('latência')).not.toBeInTheDocument()
  })
})
