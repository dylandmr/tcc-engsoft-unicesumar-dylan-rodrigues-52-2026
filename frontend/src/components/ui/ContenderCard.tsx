import { cn } from '../../lib/cn'
import type { ProviderMeta } from '../../lib/providers'
import { PROVIDER_STYLES } from '../../lib/providerStyles'
import { ModelSelect } from './ModelSelect'

interface ContenderCardProps {
  meta: ProviderMeta
  /** Arming order (0-based); -1 while the card is idle. */
  order: number
  /** From the provider catalog — unconfigured stays armable, just hinted. */
  configured: boolean
  /** True when the grid is full and this idle card cannot be armed. */
  disabled: boolean
  /** Currently selected model for this provider. */
  model: string
  /** Selectable models from the catalog. */
  models: string[]
  defaultModel: string
  onToggle: () => void
  onModelChange: (model: string) => void
}

/**
 * One provider's slot in the composer's "grid de largada": an idle card is a
 * dimmed "+" affordance; an armed card lights up in the provider's hue, shows
 * its arming order (1º, 2º…) and the model combo box (FR-020).
 */
export function ContenderCard({
  meta,
  order,
  configured,
  disabled,
  model,
  models,
  defaultModel,
  onToggle,
  onModelChange,
}: ContenderCardProps) {
  const armed = order >= 0
  const style = PROVIDER_STYLES[meta.id]

  return (
    <div
      className={cn(
        // The toggle button suppresses the global focus outline (an unrounded
        // box that slices the card); the card re-exposes it here, following
        // its own radius, only for the toggle — not the model combo box.
        'relative flex min-h-[150px] flex-col rounded-[var(--radius-panel)] border bg-deck transition duration-150 has-[button:focus-visible]:outline-2 has-[button:focus-visible]:outline-offset-2 has-[button:focus-visible]:outline-ignition',
        armed
          ? cn(style.border, style.cardGlow)
          : 'border-line hover:border-mist/60',
      )}
    >
      {armed && (
        <span
          aria-hidden="true"
          className={cn(
            'pointer-events-none absolute inset-0 rounded-[var(--radius-panel)]',
            style.chipBg,
          )}
        />
      )}
      <span
        aria-hidden="true"
        className={cn(
          'h-1 flex-none rounded-t-[calc(var(--radius-panel)-1px)]',
          armed ? style.bar : style.barDim,
        )}
      />

      <button
        type="button"
        role="checkbox"
        aria-checked={armed}
        aria-label={meta.label}
        disabled={disabled}
        onClick={onToggle}
        className={cn(
          'relative flex flex-1 flex-col items-start gap-1 px-4 pt-3 pb-3 text-left focus-visible:outline-none',
          disabled && 'cursor-not-allowed opacity-40',
        )}
      >
        <span className="flex items-center gap-2">
          {!armed && (
            <span className={cn('size-1.5 rounded-full', style.dotDim)} />
          )}
          <span
            className={cn(
              'font-display text-lg font-medium',
              armed ? 'text-bright' : 'text-mist',
            )}
          >
            {meta.label}
          </span>
        </span>
        {!configured && (
          <span className="font-mono text-[10px] text-mist/70">
            não configurado
          </span>
        )}
        {armed ? (
          <span
            className={cn(
              'mt-auto rounded-full border px-3 py-0.5 font-mono text-xs font-medium text-bright',
              style.pill,
            )}
          >
            {order + 1}º
          </span>
        ) : (
          <span className="mt-auto font-mono text-base text-mist/75">+</span>
        )}
      </button>

      {armed && (
        <div className="relative px-3 pb-3">
          <ModelSelect
            providerId={meta.id}
            label={`Modelo de ${meta.label}`}
            value={model}
            options={models}
            defaultModel={defaultModel}
            onChange={onModelChange}
          />
        </div>
      )}
    </div>
  )
}
