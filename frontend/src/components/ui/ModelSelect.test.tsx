import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { ModelSelect } from './ModelSelect'

const OPTIONS = ['alpha-1', 'beta-2', 'm-default']

function Harness({
  initial = 'm-default',
  onChange = () => {},
}: {
  initial?: string
  onChange?: (model: string) => void
}) {
  const [value, setValue] = useState(initial)
  return (
    <ModelSelect
      providerId="GEMINI"
      label="Modelo de Gemini"
      value={value}
      options={OPTIONS}
      defaultModel="m-default"
      onChange={(model) => {
        setValue(model)
        onChange(model)
      }}
    />
  )
}

const combo = () => screen.getByRole('combobox', { name: 'Modelo de Gemini' })

describe('ModelSelect', () => {
  it('shows the selected model closed, and opens a marked listbox on click', async () => {
    render(<Harness />)
    const input = combo()
    expect(input).toHaveValue('m-default')
    expect(input).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()

    await userEvent.click(input)
    expect(input).toHaveAttribute('aria-expanded', 'true')
    const options = screen.getAllByRole('option')
    expect(options).toHaveLength(3)
    // The catalog default is marked "padrão"; the selected option is flagged.
    expect(options[2]).toHaveTextContent('padrão')
    expect(options[2]).toHaveAttribute('aria-selected', 'true')
    expect(options[0]).toHaveAttribute('aria-selected', 'false')
    // The active descendant starts on the selected model.
    expect(input).toHaveAttribute('aria-activedescendant', options[2].id)
  })

  it('filters options live and reports when nothing matches', async () => {
    render(<Harness />)
    const input = combo()
    await userEvent.type(input, 'beta')
    expect(screen.getAllByRole('option')).toHaveLength(1)
    expect(screen.getByRole('option', { name: /beta-2/ })).toBeInTheDocument()

    await userEvent.clear(input)
    await userEvent.type(input, 'zzz')
    expect(screen.queryAllByRole('option')).toHaveLength(0)
    expect(screen.getByText('nenhum modelo encontrado')).toBeInTheDocument()
    expect(input).not.toHaveAttribute('aria-activedescendant')
    // Arrowing / Enter over an empty list is a guarded no-op.
    await userEvent.keyboard('{ArrowDown}{Enter}')
    expect(screen.getByRole('listbox')).toBeInTheDocument()
  })

  it('selects with ArrowDown + Enter, wrapping past the end', async () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    const input = combo()
    await userEvent.click(input)
    // Active starts at m-default (last) — ArrowDown wraps to the first.
    await userEvent.keyboard('{ArrowDown}')
    expect(input).toHaveAttribute(
      'aria-activedescendant',
      screen.getAllByRole('option')[0].id,
    )
    await userEvent.keyboard('{Enter}')
    expect(onChange).toHaveBeenCalledWith('alpha-1')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(input).toHaveValue('alpha-1')
  })

  it('moves up with ArrowUp, wrapping before the start', async () => {
    const onChange = vi.fn()
    render(<Harness initial="alpha-1" onChange={onChange} />)
    const input = combo()
    // ArrowDown on a closed combo opens it on the current selection (index 0).
    await userEvent.click(input)
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    await userEvent.keyboard('{ArrowDown}')
    expect(input).toHaveAttribute(
      'aria-activedescendant',
      screen.getAllByRole('option')[0].id,
    )
    // ArrowUp wraps from the first option to the last.
    await userEvent.keyboard('{ArrowUp}{Enter}')
    expect(onChange).toHaveBeenCalledWith('m-default')
  })

  it('opens on ArrowUp from a closed, focused combo', async () => {
    render(<Harness />)
    combo().focus()
    await userEvent.keyboard('{ArrowUp}')
    expect(screen.getByRole('listbox')).toBeInTheDocument()
  })

  it('does nothing on Enter or Escape while closed', async () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    combo().focus()
    await userEvent.keyboard('{Enter}{Escape}')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('Escape closes without changing the selection', async () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    const input = combo()
    await userEvent.click(input)
    // A second click on the already-open combo keeps it open.
    await userEvent.click(input)
    expect(screen.getByRole('listbox')).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(onChange).not.toHaveBeenCalled()
    expect(input).toHaveValue('m-default')
  })

  it('closes on blur, and selects by mouse without losing the click to blur', async () => {
    const onChange = vi.fn()
    render(
      <div>
        <Harness onChange={onChange} />
        <button type="button">fora</button>
      </div>,
    )
    const input = combo()
    await userEvent.click(input)
    await userEvent.click(screen.getByRole('option', { name: /beta-2/ }))
    expect(onChange).toHaveBeenCalledWith('beta-2')
    expect(input).toHaveValue('beta-2')

    await userEvent.click(input)
    expect(screen.getByRole('listbox')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'fora' }))
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })
})
