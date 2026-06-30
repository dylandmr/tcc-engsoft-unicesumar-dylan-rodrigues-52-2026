import type { ReactElement, ReactNode } from 'react'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SessionProvider } from '../src/auth/SessionContext'
import { AppRoutes } from '../src/App'

/** Render a tree wrapped in a router (no session bootstrapping). */
export function renderWithRouter(
  ui: ReactElement,
  { route = '/' }: { route?: string } = {},
) {
  return render(<MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>)
}

/** Render a tree wrapped in router + live session provider. */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/' }: { route?: string } = {},
) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[route]}>
      <SessionProvider>{children}</SessionProvider>
    </MemoryRouter>
  )
  return render(ui, { wrapper })
}

/** Mount the full route table in a MemoryRouter + live SessionProvider. */
export function renderApp({ route = '/' }: { route?: string } = {}) {
  return render(
    <SessionProvider>
      <MemoryRouter initialEntries={[route]}>
        <AppRoutes />
      </MemoryRouter>
    </SessionProvider>,
  )
}
