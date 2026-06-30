import { cn } from '../../lib/cn'
import type { ProviderMeta } from '../../lib/providers'
import { PROVIDER_STYLES } from '../../lib/providerStyles'

interface ProviderChipProps {
  meta: ProviderMeta
  selected: boolean
  disabled: boolean
  onToggle: () => void
}

/** A selectable provider pill in the composer's model picker. */
export function ProviderChip({
  meta,
  selected,
  disabled,
  onToggle,
}: ProviderChipProps) {
  const style = PROVIDER_STYLES[meta.id]
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={selected}
      aria-label={meta.label}
      disabled={disabled}
      onClick={onToggle}
      className={cn(
        'inline-flex items-center gap-2 rounded-full border px-4 py-2 font-body text-sm transition',
        selected ? cn(style.border, 'text-bright') : 'border-line text-mist',
        disabled && 'cursor-not-allowed opacity-40',
      )}
    >
      <span
        className={cn(
          'size-2 rounded-full',
          selected ? style.dot : 'bg-mist',
        )}
      />
      {meta.label}
    </button>
  )
}
