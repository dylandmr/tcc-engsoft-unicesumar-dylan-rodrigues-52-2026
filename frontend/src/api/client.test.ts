import { describe, expect, it, beforeEach } from 'vitest'
import { http, HttpResponse, server } from '../../testing/server'
import {
  ApiError,
  createComparison,
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

  it('creates a comparison', async () => {
    const created = await createComparison('hi', ['CLAUDE', 'CHATGPT'])
    expect(created).toEqual({
      comparisonId: 'c_test',
      providers: ['CLAUDE', 'CHATGPT'],
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
