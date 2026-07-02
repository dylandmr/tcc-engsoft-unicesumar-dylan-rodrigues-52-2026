import type {
  ComparisonSummary,
  CreatedComparison,
  ModelSelection,
  ProviderCatalogEntry,
  ProviderId,
  User,
} from '../types'

/** Error carrying the contract `error` code and HTTP status. */
export class ApiError extends Error {
  readonly code: string
  readonly status: number
  constructor(code: string, status: number) {
    super(code)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

/** Read the CSRF token the backend seeds in the XSRF-TOKEN cookie. */
function xsrfHeader(): Record<string, string> {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {}
}

async function readErrorCode(res: Response): Promise<string> {
  const body = (await res.json().catch(() => null)) as { error?: string } | null
  return body?.error ?? `http_${res.status}`
}

interface RequestOptions {
  method?: string
  body?: unknown
  csrf?: boolean
}

async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  if (opts.csrf) Object.assign(headers, xsrfHeader())

  const res = await fetch(path, {
    method: opts.method ?? 'GET',
    credentials: 'include',
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  })

  if (!res.ok) throw new ApiError(await readErrorCode(res), res.status)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export function login(username: string, password: string): Promise<User> {
  return request<User>('/api/auth/login', {
    method: 'POST',
    body: { username, password },
    csrf: true,
  })
}

export function me(): Promise<User> {
  return request<User>('/api/auth/me')
}

export function logout(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST', csrf: true })
}

/** Provider catalog for the composer: configured flag, default + models (FR-020). */
export async function getProviders(): Promise<ProviderCatalogEntry[]> {
  const data = await request<{ providers: ProviderCatalogEntry[] }>(
    '/api/providers',
  )
  return data.providers
}

export function createComparison(
  prompt: string,
  providers: ProviderId[],
  models: ModelSelection,
): Promise<CreatedComparison> {
  return request<CreatedComparison>('/api/comparisons', {
    method: 'POST',
    body: { prompt, providers, models },
    csrf: true,
  })
}

export async function listComparisons(): Promise<ComparisonSummary[]> {
  const data = await request<{ comparisons: ComparisonSummary[] }>(
    '/api/comparisons',
  )
  return data.comparisons
}

/** Permanently delete one owned comparison and everything recorded for it (FR-022). */
export function deleteComparison(id: string): Promise<void> {
  return request<void>(`/api/comparisons/${id}`, {
    method: 'DELETE',
    csrf: true,
  })
}

/** Permanently clear the caller's entire history — idempotent 204 (FR-022). */
export function clearComparisons(): Promise<void> {
  return request<void>('/api/comparisons', { method: 'DELETE', csrf: true })
}
