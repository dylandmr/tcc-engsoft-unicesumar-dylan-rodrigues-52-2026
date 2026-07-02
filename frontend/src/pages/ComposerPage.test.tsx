import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, server } from '../../testing/server'
import { renderApp } from '../../testing/render'

async function reachComposer() {
  renderApp({ route: '/' })
  await screen.findByRole('heading', {
    name: /O que os modelos devem responder/,
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
})

/** Force the reduced-motion path so submits navigate without the 1.1s hold. */
const stubReducedMotion = () =>
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockReturnValue({
      matches: true,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
    }),
  )

describe('ComposerPage', () => {
  it('blocks an empty prompt with a validation message', async () => {
    await reachComposer()
    await userEvent.click(screen.getByRole('button', { name: /Comparar/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Digite um prompt/,
    )
  })

  it('blocks running with no providers selected', async () => {
    await reachComposer()
    await userEvent.type(
      screen.getByLabelText('Prompt'),
      'Why is the sky blue?',
    )
    await userEvent.click(screen.getByRole('button', { name: /Comparar/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /pelo menos um modelo/,
    )
  })

  it('arms and disarms a contender card, tracking the order badge', async () => {
    await reachComposer()
    expect(screen.getByText(/0 ESCOLHIDO/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    expect(screen.getByText(/1 ESCOLHIDO/)).toBeInTheDocument()
    expect(screen.getByText('1º')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'ChatGPT' }))
    expect(screen.getByText('2º')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    expect(screen.getByText(/1 ESCOLHIDO/)).toBeInTheDocument()
    expect(screen.queryByText('2º')).not.toBeInTheDocument()
  })

  it('prevents selecting a fifth provider', async () => {
    await reachComposer()
    for (const name of ['Gemini', 'ChatGPT', 'Claude', 'Grok']) {
      await userEvent.click(screen.getByRole('checkbox', { name }))
    }
    expect(screen.getByText(/4 ESCOLHIDO/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'DeepSeek' })).toBeDisabled()
  })

  it('offers the catalog models in an armed card’s combo box', async () => {
    await reachComposer()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    const combo = screen.getByRole('combobox', { name: 'Modelo de Gemini' })
    expect(combo).toHaveValue('gemini-2.5-flash')
    await userEvent.click(combo)
    // Live catalog entries beyond the static fallback, default marked.
    const pro = await screen.findByRole('option', { name: /gemini-2\.5-pro/ })
    expect(pro).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /padrão/ })).toHaveTextContent(
      'gemini-2.5-flash',
    )
  })

  it('hints unconfigured providers from the catalog (armable all the same)', async () => {
    await reachComposer()
    expect(await screen.findByText('não configurado')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Grok' }))
    expect(screen.getByText('1º')).toBeInTheDocument()
  })

  it('keeps composing on static defaults when the catalog fetch fails', async () => {
    server.use(
      http.get('/api/providers', () =>
        HttpResponse.json({ error: 'boom' }, { status: 500 }),
      ),
    )
    await reachComposer()
    await userEvent.click(screen.getByRole('checkbox', { name: 'ChatGPT' }))
    expect(
      screen.getByRole('combobox', { name: 'Modelo de ChatGPT' }),
    ).toHaveValue('gpt-4o-mini')
    expect(screen.queryByText('não configurado')).not.toBeInTheDocument()
  })

  it('runs a comparison, holding the launch overlay before the arena', async () => {
    await reachComposer()
    await userEvent.type(
      screen.getByLabelText('Prompt'),
      'Explain entanglement',
    )
    await userEvent.click(screen.getByRole('checkbox', { name: 'Claude' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    await userEvent.click(screen.getByRole('button', { name: /Comparar/ }))
    expect(await screen.findByRole('status')).toHaveTextContent(
      'INICIANDO TRANSMISSÃO · 2 MODELOS',
    )
    await waitFor(
      () =>
        expect(
          screen.getByRole('region', { name: 'Claude' }),
        ).toBeInTheDocument(),
      { timeout: 3000 },
    )
  })

  it('sends the armed providers’ models and passes them to the arena', async () => {
    stubReducedMotion()
    let body: { models?: Record<string, string> } | null = null
    server.use(
      http.post('/api/comparisons', async ({ request }) => {
        body = (await request.json()) as typeof body
        return HttpResponse.json(
          { comparisonId: 'c_test', providers: ['GEMINI'] },
          { status: 201 },
        )
      }),
      http.get('/api/comparisons/:id/stream', () =>
        HttpResponse.json({}, { status: 500 }),
      ),
    )
    await reachComposer()
    await userEvent.type(screen.getByLabelText('Prompt'), 'Oi')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    const combo = screen.getByRole('combobox', { name: 'Modelo de Gemini' })
    await userEvent.click(combo)
    await userEvent.click(
      await screen.findByRole('option', { name: /gemini-2\.5-pro/ }),
    )
    await userEvent.click(screen.getByRole('button', { name: /Comparar/ }))
    const gemini = await screen.findByRole('region', { name: 'Gemini' })
    expect(body!.models).toEqual({ GEMINI: 'gemini-2.5-pro' })
    // The requested model rides the navigation state into the lane header.
    expect(gemini).toHaveTextContent('gemini-2.5-pro')
  })

  it('surfaces a server error and cancels the launch overlay', async () => {
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
    await userEvent.click(screen.getByRole('button', { name: /Comparar/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Não foi possível iniciar a comparação/,
    )
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('signs out back to the login screen', async () => {
    await reachComposer()
    await userEvent.click(screen.getByRole('button', { name: 'sair' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Usuário')).toBeInTheDocument(),
    )
  })
})
