import { describe, expect, it } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse, server } from '../../testing/server'
import { SessionProvider, useSession } from './SessionContext'

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <SessionProvider>{children}</SessionProvider>
)

describe('useSession', () => {
  it('throws when used outside a SessionProvider', () => {
    expect(() => renderHook(() => useSession())).toThrow(/SessionProvider/)
  })

  it('bootstraps the signed-in user from /auth/me', async () => {
    const { result } = renderHook(() => useSession(), { wrapper })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.user).toEqual({ username: 'alice' })
  })

  it('bootstraps to signed-out when /auth/me is 401', async () => {
    server.use(
      http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })),
    )
    const { result } = renderHook(() => useSession(), { wrapper })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.user).toBeNull()
  })

  it('signs in and out', async () => {
    server.use(
      http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })),
    )
    const { result } = renderHook(() => useSession(), { wrapper })
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(() => result.current.signIn('alice', 'pw'))
    expect(result.current.user).toEqual({ username: 'alice' })

    await act(() => result.current.signOut())
    expect(result.current.user).toBeNull()
  })
})
