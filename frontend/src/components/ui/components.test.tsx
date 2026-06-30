import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button } from './Button'
import { Panel } from './Panel'
import { Backdrop } from './Backdrop'
import { Logo } from './Logo'
import { LoadingScreen } from './LoadingScreen'
import { PromptInput } from './PromptInput'
import { ProviderChip } from './ProviderChip'
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

describe('ProviderChip', () => {
  it('renders selected vs unselected, and toggles on click', async () => {
    const onToggle = vi.fn()
    const { rerender } = render(
      <ProviderChip
        meta={providerMeta('CLAUDE')}
        selected={false}
        disabled={false}
        onToggle={onToggle}
      />,
    )
    const chip = screen.getByRole('checkbox', { name: 'Claude' })
    expect(chip).toHaveAttribute('aria-checked', 'false')
    await userEvent.click(chip)
    expect(onToggle).toHaveBeenCalled()

    rerender(
      <ProviderChip
        meta={providerMeta('CLAUDE')}
        selected={true}
        disabled={false}
        onToggle={onToggle}
      />,
    )
    expect(screen.getByRole('checkbox')).toHaveAttribute('aria-checked', 'true')
  })

  it('is disabled when the selection limit is reached', () => {
    render(
      <ProviderChip
        meta={providerMeta('GROK')}
        selected={false}
        disabled={true}
        onToggle={vi.fn()}
      />,
    )
    expect(screen.getByRole('checkbox')).toBeDisabled()
  })
})

const lane = (over: Partial<LaneState>): LaneState => ({
  provider: 'CLAUDE',
  status: 'live',
  text: '',
  errorMessage: null,
  responseTimeMs: null,
  elapsedMs: 0,
  first: false,
  ...over,
})

describe('ProviderLane', () => {
  it('renders a live lane with a streaming status', () => {
    render(<ProviderLane lane={lane({ status: 'live', elapsedMs: 500 })} />)
    expect(screen.getByText('Claude')).toBeInTheDocument()
    expect(screen.getByText(/live · streaming/)).toBeInTheDocument()
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
    expect(screen.getByText('first to respond')).toBeInTheDocument()
    expect(screen.getByText('2 tokens')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'copy' }))
    expect(writeText).toHaveBeenCalledWith('two words')
  })

  it('renders an empty-response lane distinctly', () => {
    render(<ProviderLane lane={lane({ status: 'empty' })} />)
    expect(screen.getByText('No content returned.')).toBeInTheDocument()
  })

  it('renders an error lane', () => {
    render(
      <ProviderLane
        lane={lane({ status: 'error', errorMessage: 'rate_limited' })}
      />,
    )
    expect(screen.getByText(/rate_limited/)).toBeInTheDocument()
    expect(screen.getByText('error')).toBeInTheDocument()
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
    expect(screen.getByText('timeout')).toBeInTheDocument()
  })
})
