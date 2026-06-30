import { describe, expect, it } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { http, server } from '../../testing/server'
import { sseResponse } from '../../testing/sse'
import { useArena } from './useArena'

describe('useArena', () => {
  it('fills lanes independently from the SSE stream and completes', async () => {
    server.use(
      http.get('/api/comparisons/:id/stream', () =>
        sseResponse([
          {
            event: 'result',
            data: {
              provider: 'CLAUDE',
              outcome: 'SUCCESS',
              responseText: 'Hi from Claude',
              errorMessage: null,
              responseTimeMs: 1840,
            },
          },
          { event: 'done', data: { comparisonId: 'c_test', completed: 1 } },
        ]),
      ),
    )
    const { result, unmount } = renderHook(() =>
      useArena('c_test', ['CLAUDE', 'CHATGPT']),
    )
    await waitFor(() => expect(result.current.done).toBe(true))
    expect(result.current.lanes.CLAUDE.status).toBe('done')
    expect(result.current.lanes.CLAUDE.text).toBe('Hi from Claude')
    expect(result.current.lanes.CHATGPT.status).toBe('live')
    unmount()
  })

  it('marks live lanes as errored when the stream fails', async () => {
    server.use(
      http.get('/api/comparisons/:id/stream', () =>
        new Response(null, { status: 404 }),
      ),
    )
    const { result } = renderHook(() => useArena('missing', ['CLAUDE']))
    await waitFor(() => expect(result.current.done).toBe(true))
    expect(result.current.lanes.CLAUDE.status).toBe('error')
  })
})
