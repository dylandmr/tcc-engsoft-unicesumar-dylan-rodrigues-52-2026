import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, server } from '../../testing/server'
import { renderApp } from '../../testing/render'

describe('LoginPage', () => {
  it('signs in with valid credentials and routes to the composer', async () => {
    renderApp({ route: '/login' })
    await userEvent.type(screen.getByLabelText('Usuário'), 'alice')
    await userEvent.type(screen.getByLabelText('Senha'), 'pw')
    await userEvent.click(
      screen.getByRole('button', { name: /Entrar na arena/ }),
    )
    await waitFor(() =>
      expect(
        screen.getByRole('heading', {
          name: /O que os modelos devem responder/,
        }),
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
    await userEvent.type(screen.getByLabelText('Usuário'), 'bad')
    await userEvent.type(screen.getByLabelText('Senha'), 'creds')
    await userEvent.click(
      screen.getByRole('button', { name: /Entrar na arena/ }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Usuário ou senha inválidos/,
    )
  })
})
