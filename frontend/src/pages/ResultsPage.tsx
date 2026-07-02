import { Navigate, useLocation, useParams } from 'react-router-dom'
import type { ModelSelection, ProviderId } from '../types'
import { Arena } from '../components/Arena'

interface ResultsNavState {
  providers?: ProviderId[]
  prompt?: string
  /** Requested model per provider (FR-020); absent on history replay. */
  models?: ModelSelection
}

/**
 * Results route. The selected providers (and prompt) are passed via router
 * state from the composer or history, so the arena can render lanes without a
 * round-trip. Direct/refresh navigation without state redirects to the composer.
 */
export function ResultsPage() {
  const { id } = useParams<{ id: string }>()
  const nav = useLocation().state as ResultsNavState | null
  if (!nav?.providers) return <Navigate to="/" replace />
  return (
    <Arena
      comparisonId={id!}
      providers={nav.providers}
      prompt={nav.prompt ?? ''}
      models={nav.models}
    />
  )
}
