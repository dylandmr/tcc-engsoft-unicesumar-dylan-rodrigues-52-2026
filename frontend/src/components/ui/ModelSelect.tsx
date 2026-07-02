import { useEffect, useId, useState, type KeyboardEvent } from 'react'
import type { ProviderId } from '../../types'
import { PROVIDER_STYLES } from '../../lib/providerStyles'
import { cn } from '../../lib/cn'

interface ModelSelectProps {
  providerId: ProviderId
  /** Accessible name, e.g. "Modelo de Gemini". */
  label: string
  /** The user's explicit pick — empty string until they choose (FR-020). */
  value: string
  /** Selectable models (provider catalog; live lists can be 40+ entries). */
  options: string[]
  onChange: (model: string) => void
}

/** Shown while no model has been chosen — there is no default (FR-020). */
export const MODEL_PLACEHOLDER = 'selecionar modelo…'

/**
 * Per-provider model combo box (FR-020): a mono text field that starts empty
 * (the user must choose — no default) and, when opened, filters the
 * provider's catalog live. ARIA 1.2 editable-combobox pattern — the input is
 * the combobox, the popup is a listbox navigated via aria-activedescendant.
 */
export function ModelSelect({
  providerId,
  label,
  value,
  options,
  onChange,
}: ModelSelectProps) {
  const style = PROVIDER_STYLES[providerId]
  const id = useId()
  const listboxId = `${id}-listbox`
  const optionId = (index: number) => `${id}-opt-${index}`

  const [open, setOpen] = useState(false)
  const [filter, setFilter] = useState('')
  const [active, setActive] = useState(0)

  const query = filter.trim().toLowerCase()
  const filtered = options.filter((m) => m.toLowerCase().includes(query))

  const openList = () => {
    setFilter('')
    setActive(Math.max(0, options.indexOf(value)))
    setOpen(true)
  }
  // Closing never auto-picks: an empty-value combo that loses focus stays
  // empty — the choice is always the user's (FR-020).
  const close = () => {
    setOpen(false)
    setFilter('')
  }
  const select = (model: string) => {
    onChange(model)
    close()
  }

  // Keep the active option in view while arrowing through long lists.
  useEffect(() => {
    if (!open) return
    document
      .getElementById(optionId(active))
      ?.scrollIntoView({ block: 'nearest' })
  })

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      if (!open) {
        openList()
      } else if (filtered.length > 0) {
        const delta = event.key === 'ArrowDown' ? 1 : -1
        setActive((i) => (i + delta + filtered.length) % filtered.length)
      }
    } else if (event.key === 'Enter' && open) {
      event.preventDefault()
      if (filtered[active] !== undefined) select(filtered[active])
    } else if (event.key === 'Escape' && open) {
      event.stopPropagation()
      close()
    }
  }

  return (
    <div className="relative">
      <input
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-autocomplete="list"
        aria-activedescendant={
          open && filtered.length > 0 ? optionId(active) : undefined
        }
        aria-label={label}
        spellCheck={false}
        autoComplete="off"
        value={open ? filter : value}
        placeholder={value || MODEL_PLACEHOLDER}
        onChange={(event) => {
          setFilter(event.target.value)
          setActive(0)
          setOpen(true)
        }}
        onClick={() => {
          if (!open) openList()
        }}
        onKeyDown={onKeyDown}
        onBlur={close}
        className={cn(
          'w-full cursor-pointer truncate rounded-lg border bg-void/60 px-2.5 py-1.5 font-mono text-xs text-bright transition-colors duration-150 placeholder:text-mist/50 focus:outline-none',
          open
            ? cn(style.border, 'cursor-text')
            : 'border-line hover:border-mist/60 focus-visible:border-ignition',
        )}
      />
      {open && (
        <ul
          role="listbox"
          id={listboxId}
          aria-label={label}
          className="absolute inset-x-0 top-full z-20 mt-1 max-h-52 overflow-y-auto rounded-lg border border-line bg-deck py-1 shadow-[0_12px_32px_-8px_rgba(0,0,0,0.9)]"
        >
          {filtered.length === 0 && (
            <li className="px-3 py-2 font-mono text-xs text-mist">
              nenhum modelo encontrado
            </li>
          )}
          {filtered.map((model, index) => (
            <li
              key={model}
              id={optionId(index)}
              role="option"
              aria-selected={model === value}
              // preventDefault keeps focus on the input so blur-close never
              // races the click selection.
              onMouseDown={(event) => {
                event.preventDefault()
                select(model)
              }}
              className={cn(
                'cursor-pointer truncate px-3 py-1.5 font-mono text-xs',
                index === active
                  ? cn(style.chipBg, 'text-bright')
                  : 'text-mist',
                model === value && style.text,
              )}
            >
              {model}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
