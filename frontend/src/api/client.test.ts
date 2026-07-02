import { describe, expect, it, beforeEach } from 'vitest'
import { http, HttpResponse, server } from '../../testing/server'
import {
  ApiError,
  createComparison,
  getProviders,
  listComparisons,
  login,
  logout,
  me,
} from './client'

beforeEach(() => {
  // Reset any cookies between tests.
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('api client', () => {
  it('logs in and echoes the CSRF token from the cookie', async () => {
    document.cookie = 'XSRF-TOKEN=tok%2042'
    let header: string | null = null
    server.use(
      http.post('/api/auth/login', ({ request }) => {
        header = request.headers.get('X-XSRF-TOKEN')
        return HttpResponse.json({ username: 'alice' })
      }),
    )
    const user = await login('alice', 'pw')
    expect(user).toEqual({ username: 'alice' })
    expect(header).toBe('tok 42')
  })

  it('omits the CSRF header when no cookie is present', async () => {
    let hasHeader = true
    server.use(
      http.post('/api/auth/login', ({ request }) => {
        hasHeader = request.headers.has('X-XSRF-TOKEN')
        return HttpResponse.json({ username: 'alice' })
      }),
    )
    await login('alice', 'pw')
    expect(hasHeader).toBe(false)
  })

  it('throws ApiError with the contract error code on 401', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json({ error: 'invalid_credentials' }, { status: 401 }),
      ),
    )
    await expect(login('x', 'y')).rejects.toMatchObject({
      code: 'invalid_credentials',
      status: 401,
    })
  })

  it('falls back to an http_<status> code when the body is not JSON', async () => {
    server.use(
      http.get('/api/auth/me', () =>
        HttpResponse.text('boom', { status: 500 }),
      ),
    )
    await expect(me()).rejects.toBeInstanceOf(ApiError)
    await expect(me()).rejects.toMatchObject({ code: 'http_500' })
  })

  it('reads the current user', async () => {
    await expect(me()).resolves.toEqual({ username: 'alice' })
  })

  it('logs out with no content', async () => {
    await expect(logout()).resolves.toBeUndefined()
  })

  it('creates a comparison, sending the per-provider models map (FR-020)', async () => {
    let body: unknown = null
    server.use(
      http.post('/api/comparisons', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(
          { comparisonId: 'c_test', providers: ['CLAUDE', 'CHATGPT'] },
          { status: 201 },
        )
      }),
    )
    const created = await createComparison('hi', ['CLAUDE', 'CHATGPT'], {
      CLAUDE: 'claude-3-5-haiku-latest',
      CHATGPT: 'gpt-4o-mini',
    })
    expect(created).toEqual({
      comparisonId: 'c_test',
      providers: ['CLAUDE', 'CHATGPT'],
    })
    expect(body).toEqual({
      prompt: 'hi',
      providers: ['CLAUDE', 'CHATGPT'],
      models: { CLAUDE: 'claude-3-5-haiku-latest', CHATGPT: 'gpt-4o-mini' },
    })
  })

  it('fetches the provider catalog (FR-020)', async () => {
    const catalog = await getProviders()
    expect(catalog).toHaveLength(5)
    expect(catalog[0]).toEqual({
      provider: 'GEMINI',
      configured: true,
      models: [
        'gemini-2.0-flash',
        'gemini-2.5-flash',
        'gemini-2.5-flash-lite',
        'gemini-2.5-pro',
      ],
    })
    // Unconfigured providers report an empty model list — never a default.
    expect(catalog[3]).toEqual({
      provider: 'GROK',
      configured: false,
      models: [],
    })
  })

  it('lists comparisons', async () => {
    server.use(
      http.get('/api/comparisons', () =>
        HttpResponse.json({
          comparisons: [
            {
              id: 'c1',
              prompt: 'p',
              providers: ['CLAUDE'],
              createdAt: '2026-06-30T12:00:00Z',
            },
          ],
        }),
      ),
    )
    const items = await listComparisons()
    expect(items).toHaveLength(1)
    expect(items[0].id).toBe('c1')
  })
})
