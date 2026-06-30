import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, server } from '../../testing/server'
import { renderApp } from '../../testing/render'

async function reachComposer() {
  renderApp({ route: '/' })
  await screen.findByRole('heading', { name: /What should the models answer/ })
}

describe('ComposerPage', () => {
  it('blocks an empty prompt with a validation message', async () => {
    await reachComposer()
    await userEvent.click(
      screen.getByRole('button', { name: /Run comparison/ }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(/Enter a prompt/)
  })

  it('blocks running with no providers selected', async () => {
    await reachComposer()
    await userEvent.type(
      screen.getByLabelText('Prompt'),
      'Why is the sky blue?',
    )
    await userEvent.click(
      screen.getByRole('button', { name: /Run comparison/ }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /at least one model/,
    )
  })

  it('toggles a provider on and off', async () => {
    await reachComposer()
    expect(screen.getByText(/0 CHOSEN/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    expect(screen.getByText(/1 CHOSEN/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    expect(screen.getByText(/0 CHOSEN/)).toBeInTheDocument()
  })

  it('prevents selecting a fifth provider', async () => {
    await reachComposer()
    for (const name of ['Gemini', 'ChatGPT', 'Claude', 'Grok']) {
      await userEvent.click(screen.getByRole('checkbox', { name }))
    }
    expect(screen.getByText(/4 CHOSEN/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'DeepSeek' })).toBeDisabled()
  })

  it('runs a comparison and opens the results arena', async () => {
    await reachComposer()
    await userEvent.type(
      screen.getByLabelText('Prompt'),
      'Explain entanglement',
    )
    await userEvent.click(screen.getByRole('checkbox', { name: 'Claude' }))
    await userEvent.click(
      screen.getByRole('button', { name: /Run comparison/ }),
    )
    await waitFor(() =>
      expect(
        screen.getByRole('region', { name: 'Claude' }),
      ).toBeInTheDocument(),
    )
  })

  it('surfaces a server error when the run cannot start', async () => {
    server.use(
      http.post('/api/comparisons', () =>
        HttpResponse.json({ error: 'boom' }, { status: 500 }),
      ),
    )
    await reachComposer()
    await userEvent.type(
      screen.getByLabelText('Prompt'),
      'Explain entanglement',
    )
    await userEvent.click(screen.getByRole('checkbox', { name: 'Claude' }))
    await userEvent.click(
      screen.getByRole('button', { name: /Run comparison/ }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Could not start the comparison/,
    )
  })

  it('signs out back to the login screen', async () => {
    await reachComposer()
    await userEvent.click(screen.getByRole('button', { name: 'sign out' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Username')).toBeInTheDocument(),
    )
  })
})
