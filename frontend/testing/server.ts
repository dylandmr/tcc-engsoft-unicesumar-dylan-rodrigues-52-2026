import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { sseResponse } from './sse'

/**
 * Default happy-path handlers. Individual tests override these with
 * `server.use(...)` to exercise error / edge behaviour.
 */
export const handlers = [
  http.post('/api/auth/login', () => HttpResponse.json({ username: 'alice' })),
  http.get('/api/auth/me', () => HttpResponse.json({ username: 'alice' })),
  http.post('/api/auth/logout', () => new HttpResponse(null, { status: 204 })),
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
