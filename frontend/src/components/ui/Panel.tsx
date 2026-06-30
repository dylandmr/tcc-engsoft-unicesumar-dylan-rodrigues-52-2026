import type { HTMLAttributes } from 'react'
import { cn } from '../../lib/cn'

/** A raised surface on the deck (bg/deck with a hairline border). */
export function Panel({
  className,
  children,
  ...rest
}: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'rounded-[var(--radius-panel)] border border-line bg-deck',
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  )
}
