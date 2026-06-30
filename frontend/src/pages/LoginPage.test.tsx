import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, server } from '../../testing/server'
import { renderApp } from '../../testing/render'

describe('LoginPage', () => {
  it('signs in with valid credentials and routes to the composer', async () => {
    renderApp({ route: '/login' })
    await userEvent.type(screen.getByLabelText('Username'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'pw')
    await userEvent.click(
      screen.getByRole('button', { name: /Enter the arena/ }),
    )
    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: /What should the models answer/ }),
      ).toBeInTheDocument(),
    )
  })

  it('shows a non-revealing error on invalid credentials', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({ error: 'invalid_credentials' }, { status: 401 }),
      ),
    )
    renderApp({ route: '/login' })
    await userEvent.type(screen.getByLabelText('Username'), 'bad')
    await userEvent.type(screen.getByLabelText('Password'), 'creds')
    await userEvent.click(
      screen.getByRole('button', { name: /Enter the arena/ }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Invalid username or password/,
    )
  })
})
