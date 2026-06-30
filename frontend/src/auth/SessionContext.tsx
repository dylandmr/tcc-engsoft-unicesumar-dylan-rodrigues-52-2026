import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import type { User } from '../types'
import { login, logout, me } from '../api/client'

export interface SessionValue {
  user: User | null
  loading: boolean
  signIn: (username: string, password: string) => Promise<void>
  signOut: () => Promise<void>
}

const SessionContext = createContext<SessionValue | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    me()
      .then(setUser, () => setUser(null))
      .finally(() => setLoading(false))
  }, [])

  const signIn = useCallback(async (username: string, password: string) => {
    setUser(await login(username, password))
  }, [])

  const signOut = useCallback(async () => {
    await logout()
    setUser(null)
  }, [])

  return (
    <SessionContext.Provider value={{ user, loading, signIn, signOut }}>
      {children}
    </SessionContext.Provider>
  )
}

export function useSession(): SessionValue {
  const ctx = useContext(SessionContext)
  if (!ctx) throw new Error('useSession must be used within a SessionProvider')
  return ctx
}
