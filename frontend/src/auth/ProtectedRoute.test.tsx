import { describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { http, HttpResponse, server } from '../../testing/server'
import { SessionProvider } from './SessionContext'
import { ProtectedRoute } from './ProtectedRoute'

function renderGuarded() {
  return render(
    <SessionProvider>
      <MemoryRouter initialEntries={['/p']}>
        <Routes>
          <Route
            path="/p"
            element={
              <ProtectedRoute>
                <div>secret</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>login screen</div>} />
        </Routes>
      </MemoryRouter>
    </SessionProvider>,
  )
}

describe('ProtectedRoute', () => {
  it('shows a loading state, then the protected content when signed in', async () => {
    renderGuarded()
    expect(screen.getByRole('status')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('secret')).toBeInTheDocument())
  })

  it('redirects to /login when signed out', async () => {
    server.use(
      http.get('/api/auth/me', () => new HttpResponse(null, { status: 401 })),
    )
    renderGuarded()
    await waitFor(() =>
      expect(screen.getByText('login screen')).toBeInTheDocument(),
    )
  })
})
