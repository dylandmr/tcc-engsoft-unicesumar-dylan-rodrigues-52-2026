import type { ButtonHTMLAttributes } from 'react'
import { cn } from '../../lib/cn'

type Variant = 'primary' | 'ghost'

const VARIANTS: Record<Variant, string> = {
  primary:
    'bg-ignition text-void font-semibold hover:brightness-110 disabled:opacity-50 disabled:cursor-not-allowed',
  ghost: 'text-mist hover:text-bright',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
}

export function Button({
  variant = 'primary',
  className,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-xl px-5 py-3 font-body text-base transition',
        VARIANTS[variant],
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  )
}
