import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  http,
  HttpResponse,
  providerCatalog,
  server,
} from '../../../testing/server'
import { ANALYSIS_ERROR_MESSAGE } from '../../hooks/arenaReducer'
import type { RecordedAnalysis } from '../../types'
import { KeyDifferences } from './KeyDifferences'

const recorded: RecordedAnalysis = {
  text: '## Cobertura\n\n**Modelo A** cita fontes.',
  provider: 'CLAUDE',
  model: 'claude-haiku-4-5',
  // Deliberately out of order — the legend must sort by label.
  labels: { B: 'CLAUDE', A: 'GEMINI' },
}

const judgeSelect = () => screen.findByRole('combobox', { name: 'Juíza' })
const modelCombo = () =>
  screen.getByRole('combobox', { name: 'Modelo da juíza' })
const runButton = () =>
  screen.getByRole('button', { name: 'analisar diferenças' })

describe('KeyDifferences judge picker (idle)', () => {
  it('offers only providers with models, preselects nothing, keeps the run disabled', async () => {
    render(<KeyDifferences analysis={{ phase: 'idle' }} onAnalyze={vi.fn()} />)
    expect(screen.getByText('DIFERENÇAS-CHAVE')).toBeInTheDocument()
    expect(
      screen.getByText(
        'análise por IA · respostas anonimizadas e embaralhadas · sem vencedor',
      ),
    ).toBeInTheDocument()
    expect(screen.getByText('carregando modelos…')).toBeInTheDocument()

    const select = await judgeSelect()
    expect(select).toHaveValue('')
    // GROK has no models in the catalog fixture — it cannot judge (FR-020).
    expect(
      within(select)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual(['selecionar juíza…', 'Gemini', 'ChatGPT', 'Claude', 'DeepSeek'])
    expect(
      screen.queryByRole('combobox', { name: 'Modelo da juíza' }),
    ).not.toBeInTheDocument()
    expect(runButton()).toBeDisabled()
  })

  it('enables the run only after provider AND model are picked, then dispatches', async () => {
    const onAnalyze = vi.fn()
    render(
      <KeyDifferences analysis={{ phase: 'idle' }} onAnalyze={onAnalyze} />,
    )
    await userEvent.selectOptions(await judgeSelect(), 'CLAUDE')
    expect(runButton()).toBeDisabled() // model still unpicked (FR-020)

    await userEvent.click(modelCombo())
    await userEvent.click(
      screen.getByRole('option', { name: 'claude-3-5-haiku-latest' }),
    )
    expect(runButton()).toBeEnabled()

    await userEvent.click(runButton())
    expect(onAnalyze).toHaveBeenCalledWith('CLAUDE', 'claude-3-5-haiku-latest')
  })

  it('switching the judge provider forgets the previous model pick', async () => {
    render(<KeyDifferences analysis={{ phase: 'idle' }} onAnalyze={vi.fn()} />)
    const select = await judgeSelect()
    await userEvent.selectOptions(select, 'CLAUDE')
    await userEvent.click(modelCombo())
    await userEvent.click(
      screen.getByRole('option', { name: 'claude-3-5-haiku-latest' }),
    )
    await userEvent.selectOptions(select, 'GEMINI')
    expect(modelCombo()).toHaveValue('')
    expect(runButton()).toBeDisabled()
  })

  it('recovers from a catalog failure via "tentar novamente"', async () => {
    let calls = 0
    server.use(
      http.get('/api/providers', () => {
        calls += 1
        if (calls === 1) return new HttpResponse(null, { status: 500 })
        return HttpResponse.json({ providers: providerCatalog })
      }),
    )
    render(<KeyDifferences analysis={{ phase: 'idle' }} onAnalyze={vi.fn()} />)
    expect(
      await screen.findByText(
        'Não foi possível carregar os modelos dos provedores.',
      ),
    ).toBeInTheDocument()
    await userEvent.click(
      screen.getByRole('button', { name: 'tentar novamente' }),
    )
    expect(await judgeSelect()).toBeInTheDocument()
  })
})

describe('KeyDifferences streaming', () => {
  it('renders the accumulating markdown with the live caret and no picker', () => {
    const { container } = render(
      <KeyDifferences
        analysis={{ phase: 'streaming', text: '**Modelo A** é mais direto' }}
        onAnalyze={vi.fn()}
      />,
    )
    expect(screen.getByText('analisando…')).toBeInTheDocument()
    expect(screen.getByText('Modelo A')).toBeInTheDocument() // markdown <strong>
    expect(container.querySelector('.streaming')).not.toBeNull()
    expect(
      screen.queryByRole('button', { name: 'analisar diferenças' }),
    ).not.toBeInTheDocument()
  })
})

describe('KeyDifferences done', () => {
  it('shows the analysis, the sorted anonymization legend and the judge attribution', () => {
    render(
      <KeyDifferences
        analysis={{ phase: 'done', analysis: recorded }}
        onAnalyze={vi.fn()}
      />,
    )
    expect(
      screen.getByRole('heading', { name: 'Cobertura' }),
    ).toBeInTheDocument()
    const chips = screen.getAllByText(/Modelo [AB] →/)
    expect(chips.map((chip) => chip.textContent)).toEqual([
      'Modelo A → Gemini',
      'Modelo B → Claude',
    ])
    // Legend chips carry the provider's brand hue.
    expect(screen.getByText('Modelo A → Gemini').className).toContain(
      'border-gemini/40',
    )
    expect(
      screen.getByText('juíza: Claude · claude-haiku-4-5'),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'analisar diferenças' }),
    ).not.toBeInTheDocument()
  })
})

describe('KeyDifferences error', () => {
  it('shows the retryable error and brings the picker back', async () => {
    render(
      <KeyDifferences
        analysis={{ phase: 'error', message: ANALYSIS_ERROR_MESSAGE }}
        onAnalyze={vi.fn()}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Não foi possível gerar a análise. Tente outra juíza.',
    )
    expect(await judgeSelect()).toBeInTheDocument()
    expect(runButton()).toBeDisabled()
  })
})
