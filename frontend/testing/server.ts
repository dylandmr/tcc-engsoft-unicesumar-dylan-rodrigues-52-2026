import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { sseResponse } from './sse'

/**
 * Default happy-path handlers. Individual tests override these with
 * `server.use(...)` to exercise error / edge behaviour.
 */
/** Default provider catalog (GET /api/providers) — GROK is unconfigured. */
export const providerCatalog = [
  {
    provider: 'GEMINI',
    configured: true,
    defaultModel: 'gemini-2.5-flash',
    models: [
      'gemini-2.0-flash',
      'gemini-2.5-flash',
      'gemini-2.5-flash-lite',
      'gemini-2.5-pro',
    ],
    source: 'live',
  },
  {
    provider: 'CHATGPT',
    configured: true,
    defaultModel: 'gpt-4o-mini',
    models: ['gpt-4.1-mini', 'gpt-4o', 'gpt-4o-mini'],
    source: 'live',
  },
  {
    provider: 'CLAUDE',
    configured: true,
    defaultModel: 'claude-3-5-sonnet-latest',
    models: ['claude-3-5-haiku-latest', 'claude-3-5-sonnet-latest'],
    source: 'curated',
  },
  {
    provider: 'GROK',
    configured: false,
    defaultModel: 'grok-2-latest',
    models: ['grok-2-latest', 'grok-3-mini'],
    source: 'curated',
  },
  {
    provider: 'DEEPSEEK',
    configured: true,
    defaultModel: 'deepseek-chat',
    models: ['deepseek-chat', 'deepseek-reasoner'],
    source: 'curated',
  },
]

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
]

export const server = setupServer(...handlers)

export { http, HttpResponse, sseResponse }
