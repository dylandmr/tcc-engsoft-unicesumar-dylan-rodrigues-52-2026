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
        'rounded-[var(--radius-panel)] border border-line bg-deck shadow-[inset_0_1px_0_0_rgba(255,255,255,0.04),0_8px_24px_-12px_rgba(0,0,0,0.8)]',
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  )
}
