import { describe, expect, it, vi } from 'vitest'
import {
  http,
  HttpResponse,
  recordedAnalysis,
  server,
  sseResponse,
} from '../../testing/server'
import { encodeSseEvent } from '../../testing/sse'
import { ApiError } from './client'
import { parseBlock, streamAnalysis, streamComparison } from './sse'
import type {
  AnalysisResult,
  ChunkEvent,
  DoneEvent,
  ProviderResult,
} from '../types'

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

describe('streamComparison analysis replay (FR-021)', () => {
  const replayStream = () =>
    sseResponse([
      { event: 'analysis', data: recordedAnalysis },
      { event: 'done', data: { comparisonId: 'c1', completed: 2 } },
    ])

  it('surfaces a replayed analysis event to the onAnalysis handler', async () => {
    server.use(http.get('/api/comparisons/:id/stream', replayStream))
    const analyses: AnalysisResult[] = []
    const onDone = vi.fn()
    await streamComparison('c1', {
      onChunk: vi.fn(),
      onResult: vi.fn(),
      onDone,
      onAnalysis: (analysis) => analyses.push(analysis),
    })
    expect(analyses).toEqual([recordedAnalysis])
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('leaves callers without an onAnalysis handler unaffected (additive event)', async () => {
    server.use(http.get('/api/comparisons/:id/stream', replayStream))
    const onDone = vi.fn()
    await streamComparison('c1', {
      onChunk: vi.fn(),
      onResult: vi.fn(),
      onDone,
    })
    expect(onDone).toHaveBeenCalledTimes(1)
  })
})

describe('streamAnalysis', () => {
  it('sends the picked judge as query params and dispatches chunk + terminal events', async () => {
    let requestedUrl = ''
    server.use(
      http.get('/api/comparisons/:id/analysis/stream', ({ request }) => {
        requestedUrl = request.url
        return sseResponse([
          { event: 'analysis-chunk', data: { delta: '## Cob' } },
          { event: 'analysis-chunk', data: { delta: 'ertura' } },
          { event: 'ping', data: { keepAlive: true } },
          { event: 'analysis', data: recordedAnalysis },
          { event: 'done', data: { comparisonId: 'c1' } },
        ])
      }),
    )
    const deltas: string[] = []
    const analyses: AnalysisResult[] = []
    await streamAnalysis('c1', 'CLAUDE', 'claude-haiku-4-5', {
      onChunk: (chunk) => deltas.push(chunk.delta),
      onAnalysis: (analysis) => analyses.push(analysis),
    })
    expect(deltas.join('')).toBe('## Cobertura')
    expect(analyses).toEqual([recordedAnalysis])
    const params = new URL(requestedUrl).searchParams
    expect(params.get('provider')).toBe('CLAUDE')
    expect(params.get('model')).toBe('claude-haiku-4-5')
  })

  it('throws ApiError when the judge is rejected on open (400)', async () => {
    server.use(
      http.get('/api/comparisons/:id/analysis/stream', () =>
        HttpResponse.json({ error: 'insufficient_results' }, { status: 400 }),
      ),
    )
    await expect(
      streamAnalysis('c1', 'CLAUDE', 'claude-haiku-4-5', {
        onChunk: vi.fn(),
        onAnalysis: vi.fn(),
      }),
    ).rejects.toBeInstanceOf(ApiError)
  })
})
