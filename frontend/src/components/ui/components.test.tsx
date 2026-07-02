import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button } from './Button'
import { Panel } from './Panel'
import { Backdrop } from './Backdrop'
import { Logo } from './Logo'
import { LoadingScreen } from './LoadingScreen'
import { PromptInput } from './PromptInput'
import { ContenderCard } from './ContenderCard'
import { ProviderLane } from './ProviderLane'
import { providerMeta } from '../../lib/providers'
import type { LaneState } from '../../hooks/arenaReducer'

describe('Button', () => {
  it('defaults to the primary (ignition) variant', () => {
    render(<Button>Go</Button>)
    expect(screen.getByRole('button', { name: 'Go' }).className).toContain(
      'bg-ignition',
    )
  })
  it('supports the ghost variant', () => {
    render(<Button variant="ghost">x</Button>)
    expect(screen.getByRole('button').className).toContain('text-mist')
  })
})

describe('layout primitives', () => {
  it('renders Panel, Backdrop, Logo and LoadingScreen', () => {
    render(
      <div>
        <Panel>panel-body</Panel>
        <Backdrop />
        <Logo />
        <LoadingScreen />
      </div>,
    )
    expect(screen.getByText('panel-body')).toBeInTheDocument()
    expect(screen.getByText('PROMPT ARENA')).toBeInTheDocument()
    expect(screen.getByRole('status')).toBeInTheDocument()
  })
})

describe('PromptInput', () => {
  it('shows a live character count and forwards changes', async () => {
    const onChange = vi.fn()
    render(<PromptInput value="hello" onChange={onChange} />)
    expect(screen.getByText('5 / 8000')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Prompt'), 'x')
    expect(onChange).toHaveBeenCalled()
  })
})

function renderCard(over: {
  order?: number
  configured?: boolean
  disabled?: boolean
  onToggle?: () => void
  onModelChange?: (model: string) => void
}) {
  return render(
    <ContenderCard
      meta={providerMeta('CLAUDE')}
      order={over.order ?? -1}
      configured={over.configured ?? true}
      disabled={over.disabled ?? false}
      model="claude-3-5-sonnet-latest"
      models={['claude-3-5-haiku-latest', 'claude-3-5-sonnet-latest']}
      defaultModel="claude-3-5-sonnet-latest"
      onToggle={over.onToggle ?? (() => {})}
      onModelChange={over.onModelChange ?? (() => {})}
    />,
  )
}

describe('ContenderCard', () => {
  it('renders an idle card with a "+" affordance and toggles on click', async () => {
    const onToggle = vi.fn()
    renderCard({ onToggle })
    const card = screen.getByRole('checkbox', { name: 'Claude' })
    expect(card).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByText('+')).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    await userEvent.click(card)
    expect(onToggle).toHaveBeenCalled()
  })

  it('renders an armed card with its order badge and model combo box', () => {
    renderCard({ order: 1 })
    expect(screen.getByRole('checkbox', { name: 'Claude' })).toHaveAttribute(
      'aria-checked',
      'true',
    )
    expect(screen.getByText('2º')).toBeInTheDocument()
    expect(screen.queryByText('+')).not.toBeInTheDocument()
    expect(
      screen.getByRole('combobox', { name: 'Modelo de Claude' }),
    ).toHaveValue('claude-3-5-sonnet-latest')
  })

  it('forwards model picks from the combo box', async () => {
    const onModelChange = vi.fn()
    renderCard({ order: 0, onModelChange })
    await userEvent.click(
      screen.getByRole('combobox', { name: 'Modelo de Claude' }),
    )
    await userEvent.click(screen.getByRole('option', { name: /haiku/ }))
    expect(onModelChange).toHaveBeenCalledWith('claude-3-5-haiku-latest')
  })

  it('hints an unconfigured provider yet keeps it armable', async () => {
    const onToggle = vi.fn()
    renderCard({ configured: false, onToggle })
    expect(screen.getByText('não configurado')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Claude' }))
    expect(onToggle).toHaveBeenCalled()
  })

  it('is disabled while idle when the grid is full', () => {
    renderCard({ disabled: true })
    expect(screen.getByRole('checkbox')).toBeDisabled()
  })
})

const lane = (over: Partial<LaneState>): LaneState => ({
  provider: 'CLAUDE',
  status: 'live',
  text: '',
  errorMessage: null,
  responseTimeMs: null,
  firstTokenMs: null,
  inputTokens: null,
  outputTokens: null,
  model: null,
  elapsedMs: 0,
  first: false,
  ...over,
})

describe('ProviderLane', () => {
  it('renders a live lane with a streaming status', () => {
    render(<ProviderLane lane={lane({ status: 'live', elapsedMs: 500 })} />)
    expect(screen.getByText('Claude')).toBeInTheDocument()
    expect(screen.getByText(/ao vivo · transmitindo/)).toBeInTheDocument()
  })

  it('renders a done lane with text, footer telemetry and copy', async () => {
    const writeText = vi.fn()
    Object.assign(navigator, { clipboard: { writeText } })
    render(
      <ProviderLane
        lane={lane({
          status: 'done',
          first: true,
          text: 'two words',
          responseTimeMs: 970,
        })}
        index={1}
      />,
    )
    expect(screen.getByText('0.97s')).toBeInTheDocument()
    expect(screen.getByText('primeiro a responder')).toBeInTheDocument()
    // No provider-reported count → an estimate, never labeled "tokens".
    expect(screen.getByText('~2 palavras')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'copiar' }))
    expect(writeText).toHaveBeenCalledWith('two words')
  })

  it('prefers the provider-reported token count in the footer', () => {
    render(
      <ProviderLane
        lane={lane({ status: 'done', text: 'two words', outputTokens: 128 })}
      />,
    )
    expect(screen.getByText('128 tokens')).toBeInTheDocument()
    expect(screen.queryByText(/palavras/)).not.toBeInTheDocument()
  })

  it('renders an empty-response lane distinctly', () => {
    render(<ProviderLane lane={lane({ status: 'empty' })} />)
    expect(screen.getByText('Nenhum conteúdo retornado.')).toBeInTheDocument()
  })

  it('renders the response as formatted markdown, not raw text', () => {
    render(
      <ProviderLane
        lane={lane({
          status: 'done',
          text: '**bold** then\n\n- item one\n- item two',
        })}
      />,
    )
    const strong = screen.getByText('bold')
    expect(strong.tagName).toBe('STRONG')
    expect(screen.getByText('item one')).toBeInTheDocument()
  })

  it('renders an unconfigured provider as a dimmed, disabled lane', () => {
    render(
      <ProviderLane
        lane={lane({
          status: 'disabled',
          errorMessage: 'provider_not_configured',
        })}
      />,
    )
    expect(screen.getByText('não configurado')).toBeInTheDocument()
    expect(screen.getByText(/Nenhuma chave de API/)).toBeInTheDocument()
    // Not surfaced as an error, and no telemetry/copy footer.
    expect(
      screen.queryByText('provider_not_configured'),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'copiar' }),
    ).not.toBeInTheDocument()
  })

  it('renders an error lane', () => {
    render(
      <ProviderLane
        lane={lane({ status: 'error', errorMessage: 'rate_limited' })}
      />,
    )
    expect(screen.getByText(/rate_limited/)).toBeInTheDocument()
    expect(screen.getByText('erro')).toBeInTheDocument()
  })

  it('renders a timeout lane', () => {
    render(
      <ProviderLane
        lane={lane({
          status: 'timeout',
          errorMessage: 'No response within the time limit.',
        })}
      />,
    )
    expect(screen.getByText(/time limit/)).toBeInTheDocument()
    expect(screen.getByText('tempo esgotado')).toBeInTheDocument()
  })
})

describe('ProviderLane model sublabel', () => {
  it('shows the requested model until the provider reports one', () => {
    render(
      <ProviderLane
        lane={lane({ status: 'live' })}
        requestedModel="claude-3-5-sonnet-latest"
      />,
    )
    expect(screen.getByText('claude-3-5-sonnet-latest')).toBeInTheDocument()
  })

  it('prefers the provider-reported model over the requested one', () => {
    render(
      <ProviderLane
        lane={lane({ status: 'done', model: 'claude-3-5-sonnet-20241022' })}
        requestedModel="claude-3-5-sonnet-latest"
      />,
    )
    expect(screen.getByText('claude-3-5-sonnet-20241022')).toBeInTheDocument()
    expect(
      screen.queryByText('claude-3-5-sonnet-latest'),
    ).not.toBeInTheDocument()
  })

  it('shows no sublabel when neither is known (history replay pre-result)', () => {
    render(<ProviderLane lane={lane({ status: 'live' })} />)
    expect(screen.queryByText(/claude-3-5/)).not.toBeInTheDocument()
  })
})

describe('ProviderLane auto-follow scroll', () => {
  const scrollBody = (container: HTMLElement) => {
    const body = container.querySelector('.overflow-y-auto') as HTMLDivElement
    Object.defineProperty(body, 'scrollHeight', {
      configurable: true,
      value: 480,
    })
    return body
  }

  it('pins a live lane to the newest streamed tokens', () => {
    const { container, rerender } = render(
      <ProviderLane lane={lane({ status: 'live', text: 'linha um' })} />,
    )
    const body = scrollBody(container)
    rerender(
      <ProviderLane lane={lane({ status: 'live', text: 'linha um e dois' })} />,
    )
    expect(body.scrollTop).toBe(480)
  })

  it('leaves scroll control to the reader once the lane is done', () => {
    const { container, rerender } = render(
      <ProviderLane lane={lane({ status: 'done', text: 'linha um' })} />,
    )
    const body = scrollBody(container)
    rerender(
      <ProviderLane lane={lane({ status: 'done', text: 'linha um e dois' })} />,
    )
    expect(body.scrollTop).toBe(0)
  })
})

describe('ProviderLane copy feedback', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('confirms with a transient "copiado ✓" then reverts', () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const writeText = vi.fn()
    Object.assign(navigator, { clipboard: { writeText } })
    render(<ProviderLane lane={lane({ status: 'done', text: 'abc' })} />)

    fireEvent.click(screen.getByRole('button', { name: 'copiar' }))
    expect(writeText).toHaveBeenCalledWith('abc')
    expect(
      screen.getByRole('button', { name: /copiado ✓/ }),
    ).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(1500)
    })
    expect(screen.getByRole('button', { name: 'copiar' })).toBeInTheDocument()
  })
})
