import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { sseResponse } from './sse'

/**
 * Default happy-path handlers. Individual tests override these with
 * `server.use(...)` to exercise error / edge behaviour.
 */
/**
 * Default provider catalog (GET /api/providers) — models are exactly what
 * each provider's API reports; GROK is unconfigured, so its list is empty.
 */
export const providerCatalog = [
  {
    provider: 'GEMINI',
    configured: true,
    models: [
      'gemini-2.0-flash',
      'gemini-2.5-flash',
      'gemini-2.5-flash-lite',
      'gemini-2.5-pro',
    ],
  },
  {
    provider: 'CHATGPT',
    configured: true,
    models: ['gpt-4.1-mini', 'gpt-4o', 'gpt-4o-mini'],
  },
  {
    provider: 'CLAUDE',
    configured: true,
    models: ['claude-3-5-haiku-latest', 'claude-3-5-sonnet-latest'],
  },
  {
    provider: 'GROK',
    configured: false,
    models: [],
  },
  {
    provider: 'DEEPSEEK',
    configured: true,
    models: ['deepseek-chat', 'deepseek-reasoner'],
  },
]

/**
 * A recorded FR-021 analysis, exactly as the terminal `analysis` SSE event
 * (and the results stream's replay) carries it.
 */
export const recordedAnalysis = {
  text: '## Cobertura\n\n**Modelo A** cita fontes; **Modelo B** é mais direto.',
  errorMessage: null,
  provider: 'CLAUDE',
  model: 'claude-haiku-4-5',
  labels: { A: 'GEMINI', B: 'CLAUDE' },
}

export const handlers = [
  http.post('/api/auth/login', () => HttpResponse.json({ username: 'alice' })),
  http.get('/api/auth/me', () => HttpResponse.json({ username: 'alice' })),
  http.post('/api/auth/logout', () => new HttpResponse(null, { status: 204 })),
  http.get('/api/providers', () =>
    HttpResponse.json({ providers: providerCatalog }),
  ),
  http.post('/api/comparisons', async ({ request }) => {
    const body = (await request.json()) as { providers: string[] }
    return HttpResponse.json(
      { comparisonId: 'c_test', providers: body.providers },
      { status: 201 },
    )
  }),
  http.get('/api/comparisons/:id/stream', () =>
    sseResponse([
      {
        event: 'result',
        data: {
          provider: 'CLAUDE',
          outcome: 'SUCCESS',
          responseText: 'Hello from Claude.',
          errorMessage: null,
          responseTimeMs: 1840,
        },
      },
      { event: 'done', data: { comparisonId: 'c_test', completed: 1 } },
    ]),
  ),
  http.get('/api/comparisons', () => HttpResponse.json({ comparisons: [] })),
  http.get('/api/comparisons/stats', () => HttpResponse.json({ stats: [] })),
]

export const server = setupServer(...handlers)

export { http, HttpResponse, sseResponse }
