import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Logo } from './Logo'

/** Top navigation bar with the wordmark and right-aligned nav slot. */
export function TopBar({ children }: { children?: ReactNode }) {
  return (
    <header className="flex items-center justify-between px-6 py-6">
      <Link to="/" aria-label="Início">
        <Logo />
      </Link>
      <nav className="flex items-center gap-6 font-mono text-sm text-mist">
        {children}
      </nav>
    </header>
  )
}
