import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse, server, sseResponse } from '../../testing/server'
import { encodeSseEvent } from '../../testing/sse'
import { ApiError } from './client'
import { parseBlock, streamComparison } from './sse'
import type { ChunkEvent, DoneEvent, ProviderResult } from '../types'

describe('parseBlock', () => {
  it('parses an event name and JSON data', () => {
    expect(parseBlock('event: done\ndata: {"completed":1}')).toEqual({
      event: 'done',
      data: { completed: 1 },
    })
  })

  it('returns null for blocks with no data line', () => {
    expect(parseBlock(': keep-alive comment')).toBeNull()
  })

  it('defaults the event name to "message"', () => {
    expect(parseBlock('data: 1')).toEqual({ event: 'message', data: 1 })
  })
})

describe('streamComparison', () => {
  it('dispatches chunk, result and done events, ignoring unknown ones', async () => {
    server.use(
      http.get('/api/comparisons/:id/stream', () =>
        sseResponse([
          { event: 'chunk', data: { provider: 'CLAUDE', delta: 'hi' } },
          {
            event: 'result',
            data: {
              provider: 'CLAUDE',
              outcome: 'SUCCESS',
              responseText: 'hi',
              errorMessage: null,
              responseTimeMs: 100,
            },
          },
          { event: 'ping', data: { keepAlive: true } },
          { event: 'done', data: { comparisonId: 'c1', completed: 1 } },
        ]),
      ),
    )
    const chunks: ChunkEvent[] = []
    const results: ProviderResult[] = []
    const dones: DoneEvent[] = []
    await streamComparison('c1', {
      onChunk: (c) => chunks.push(c),
      onResult: (r) => results.push(r),
      onDone: (d) => dones.push(d),
    })
    expect(chunks).toHaveLength(1)
    expect(chunks[0].delta).toBe('hi')
    expect(results).toHaveLength(1)
    expect(results[0].provider).toBe('CLAUDE')
    expect(dones[0].completed).toBe(1)
  })

  it('skips keep-alive comment blocks that carry no data', async () => {
    server.use(
      http.get(
        '/api/comparisons/:id/stream',
        () =>
          new HttpResponse(
            ': keep-alive\n\n' +
              encodeSseEvent({
                event: 'done',
                data: { comparisonId: 'c1', completed: 0 },
              }),
            { headers: { 'Content-Type': 'text/event-stream' } },
          ),
      ),
    )
    const onDone = vi.fn()
    await streamComparison('c1', {
      onChunk: vi.fn(),
      onResult: vi.fn(),
      onDone,
    })
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('throws ApiError when the stream is not found', async () => {
    server.use(
      http.get(
        '/api/comparisons/:id/stream',
        () => new Response(null, { status: 404 }),
      ),
    )
    await expect(
      streamComparison('missing', {
        onChunk: vi.fn(),
        onResult: vi.fn(),
        onDone: vi.fn(),
      }),
    ).rejects.toBeInstanceOf(ApiError)
  })
})
