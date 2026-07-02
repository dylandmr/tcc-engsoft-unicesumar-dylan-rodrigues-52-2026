import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useSession } from './SessionContext'
import { LoadingScreen } from '../components/ui/LoadingScreen'

/** Gate that redirects signed-out users to /login (FR-001). */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useSession()
  if (loading) return <LoadingScreen />
  if (!user) return <Navigate to="/login" replace />
  return children
}
