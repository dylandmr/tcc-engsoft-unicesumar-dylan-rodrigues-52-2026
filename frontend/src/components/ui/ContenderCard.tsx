import { cn } from '../../lib/cn'
import type { ProviderMeta } from '../../lib/providers'
import { PROVIDER_STYLES } from '../../lib/providerStyles'
import { ModelSelect } from './ModelSelect'

interface ContenderCardProps {
  meta: ProviderMeta
  /** Arming order (0-based); -1 while the card is idle. */
  order: number
  /** True while the provider catalog is not ready — neutral, not armable. */
  loading: boolean
  /** Why this card cannot be armed (catalog ready), or null when it can. */
  unavailableHint: string | null
  /** True when the grid is full and this idle card cannot be armed. */
  disabled: boolean
  /** The user's explicit model pick — empty until chosen (FR-020). */
  model: string
  /** Selectable models from the catalog. */
  models: string[]
  onToggle: () => void
  onModelChange: (model: string) => void
}

/**
 * One provider's slot in the composer's "grid de largada": an idle card is a
 * dimmed "+" affordance; an armed card lights up in the provider's hue, shows
 * its arming order (1º, 2º…) and the model combo box, which starts unchosen —
 * the user must pick the model explicitly (FR-020). A card whose provider
 * offers no models is unavailable and cannot be armed.
 */
export function ContenderCard({
  meta,
  order,
  loading,
  unavailableHint,
  disabled,
  model,
  models,
  onToggle,
  onModelChange,
}: ContenderCardProps) {
  const armed = order >= 0
  const unavailable = unavailableHint !== null
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
      {/* The 4px bar can't render the card's 13px corner radius itself (radii
          clamp to the box height and the corners poke past the border curve),
          so a full-size rounded overlay clips it to the card's outline. */}
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 overflow-hidden rounded-[inherit]"
      >
        <span
          className={cn(
            'absolute inset-x-0 top-0 h-1',
            armed ? style.bar : style.barDim,
          )}
        />
      </span>

      <button
        type="button"
        role="checkbox"
        aria-checked={armed}
        aria-label={meta.label}
        disabled={disabled || loading || unavailable}
        onClick={onToggle}
        className={cn(
          'relative flex flex-1 flex-col items-start gap-1 px-4 pt-4 pb-3 text-left focus-visible:outline-none',
          (disabled || unavailable) && 'cursor-not-allowed opacity-40',
          loading && 'opacity-60',
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
        {armed ? (
          <span
            className={cn(
              'mt-auto rounded-full border px-3 py-0.5 font-mono text-xs font-medium text-bright',
              style.pill,
            )}
          >
            {order + 1}º
          </span>
        ) : unavailable ? (
          <span className="mt-auto font-mono text-[10px] text-mist/70">
            {unavailableHint}
          </span>
        ) : (
          <span className="mt-auto font-mono text-base text-mist/75">
            {loading ? '…' : '+'}
          </span>
        )}
      </button>

      {armed && (
        <div className="relative px-3 pb-3">
          <ModelSelect
            providerId={meta.id}
            label={`Modelo de ${meta.label}`}
            value={model}
            options={models}
            onChange={onModelChange}
          />
        </div>
      )}
    </div>
  )
}
