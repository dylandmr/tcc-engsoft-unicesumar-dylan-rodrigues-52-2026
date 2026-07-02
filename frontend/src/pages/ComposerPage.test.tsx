import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay } from 'msw'
import {
  http,
  HttpResponse,
  providerCatalog,
  server,
} from '../../testing/server'
import { renderApp } from '../../testing/render'

async function reachComposer() {
  renderApp({ route: '/' })
  await screen.findByRole('heading', {
    name: /O que os modelos devem responder/,
  })
}

/** Wait for the provider catalog to land — cards become armable. */
async function catalogReady() {
  await waitFor(() =>
    expect(screen.getByRole('checkbox', { name: 'Gemini' })).toBeEnabled(),
  )
}

/** Open a provider's model combo box and pick an option. */
async function pickModel(provider: string, model: RegExp) {
  await userEvent.click(
    screen.getByRole('combobox', { name: `Modelo de ${provider}` }),
  )
  await userEvent.click(await screen.findByRole('option', { name: model }))
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

  it('blocks running while an armed provider has no chosen model (FR-020)', async () => {
    await reachComposer()
    await catalogReady()
    await userEvent.type(screen.getByLabelText('Prompt'), 'Oi')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    await userEvent.click(screen.getByRole('button', { name: /Comparar/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Escolha a versão de cada modelo selecionado.',
    )
  })

  it('arms and disarms a contender card, tracking the order badge', async () => {
    await reachComposer()
    await catalogReady()
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
    // All five providers must be armable to exercise the 4-of-5 limit.
    server.use(
      http.get('/api/providers', () =>
        HttpResponse.json({
          providers: providerCatalog.map((p) =>
            p.models.length > 0
              ? p
              : { ...p, configured: true, models: ['grok-2-latest'] },
          ),
        }),
      ),
    )
    await reachComposer()
    await catalogReady()
    for (const name of ['Gemini', 'ChatGPT', 'Claude', 'Grok']) {
      await userEvent.click(screen.getByRole('checkbox', { name }))
    }
    expect(screen.getByText(/4 ESCOLHIDO/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'DeepSeek' })).toBeDisabled()
  })

  it('offers exactly the catalog models in an unchosen combo box', async () => {
    await reachComposer()
    await catalogReady()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    const combo = screen.getByRole('combobox', { name: 'Modelo de Gemini' })
    // No default: the combo starts empty with a placeholder (FR-020).
    expect(combo).toHaveValue('')
    expect(combo).toHaveAttribute('placeholder', 'selecionar modelo…')
    await userEvent.click(combo)
    expect(await screen.findAllByRole('option')).toHaveLength(4)
    expect(
      screen.getByRole('option', { name: 'gemini-2.5-pro' }),
    ).toBeInTheDocument()
    // Nothing is marked as a default — no default exists.
    expect(screen.queryByText('padrão')).not.toBeInTheDocument()
  })

  it('renders an unconfigured provider as unavailable, not armable', async () => {
    await reachComposer()
    expect(await screen.findByText('não configurado')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Grok' })).toBeDisabled()
  })

  it('renders a configured provider with an empty model list as unavailable', async () => {
    server.use(
      http.get('/api/providers', () =>
        HttpResponse.json({
          providers: providerCatalog.map((p) =>
            p.provider === 'DEEPSEEK' ? { ...p, models: [] } : p,
          ),
        }),
      ),
    )
    await reachComposer()
    expect(await screen.findByText('modelos indisponíveis')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'DeepSeek' })).toBeDisabled()
  })

  it('keeps every card neutral and unarmable while the catalog loads', async () => {
    server.use(
      http.get('/api/providers', async () => {
        await delay('infinite')
        return HttpResponse.json({ providers: [] })
      }),
    )
    await reachComposer()
    expect(screen.getAllByText('…')).toHaveLength(5)
    expect(screen.getByRole('checkbox', { name: 'Gemini' })).toBeDisabled()
    expect(screen.queryByText('não configurado')).not.toBeInTheDocument()
  })

  it('surfaces a catalog failure with a retry that reloads the models', async () => {
    server.use(
      http.get(
        '/api/providers',
        () => HttpResponse.json({ error: 'boom' }, { status: 500 }),
        { once: true },
      ),
    )
    await reachComposer()
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar os modelos dos provedores.',
    )
    expect(screen.getByRole('checkbox', { name: 'Gemini' })).toBeDisabled()

    await userEvent.click(
      screen.getByRole('button', { name: 'tentar novamente' }),
    )
    await catalogReady()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('runs a comparison, holding the launch overlay before the arena', async () => {
    await reachComposer()
    await catalogReady()
    await userEvent.type(
      screen.getByLabelText('Prompt'),
      'Explain entanglement',
    )
    await userEvent.click(screen.getByRole('checkbox', { name: 'Claude' }))
    await pickModel('Claude', /claude-3-5-sonnet-latest/)
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    await pickModel('Gemini', /gemini-2\.5-flash-lite/)
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

  it('sends the explicit model picks and passes them to the arena', async () => {
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
    await catalogReady()
    await userEvent.type(screen.getByLabelText('Prompt'), 'Oi')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Gemini' }))
    await pickModel('Gemini', /gemini-2\.5-pro/)
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
    await catalogReady()
    await userEvent.type(
      screen.getByLabelText('Prompt'),
      'Explain entanglement',
    )
    await userEvent.click(screen.getByRole('checkbox', { name: 'Claude' }))
    await pickModel('Claude', /claude-3-5-sonnet-latest/)
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
