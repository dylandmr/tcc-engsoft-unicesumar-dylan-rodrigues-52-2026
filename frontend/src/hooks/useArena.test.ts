import { describe, expect, it } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import {
  http,
  HttpResponse,
  recordedAnalysis,
  server,
} from '../../testing/server'
import { sseResponse } from '../../testing/sse'
import { ANALYSIS_ERROR_MESSAGE } from './arenaReducer'
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
    await waitFor(() => expect(result.current.state.done).toBe(true))
    expect(result.current.state.lanes.CLAUDE.status).toBe('done')
    expect(result.current.state.lanes.CLAUDE.text).toBe('Hi from Claude')
    expect(result.current.state.lanes.CHATGPT.status).toBe('live')
    unmount()
  })

  it('accumulates streamed chunk deltas into the lane text', async () => {
    server.use(
      http.get('/api/comparisons/:id/stream', () =>
        sseResponse([
          { event: 'chunk', data: { provider: 'CLAUDE', delta: 'Hel' } },
          { event: 'chunk', data: { provider: 'CLAUDE', delta: 'lo' } },
          {
            event: 'result',
            data: {
              provider: 'CLAUDE',
              outcome: 'SUCCESS',
              responseText: 'Hello',
              errorMessage: null,
              responseTimeMs: 10,
            },
          },
          { event: 'done', data: { comparisonId: 'c_test', completed: 1 } },
        ]),
      ),
    )
    const { result, unmount } = renderHook(() => useArena('c_test', ['CLAUDE']))
    await waitFor(() => expect(result.current.state.done).toBe(true))
    expect(result.current.state.lanes.CLAUDE.text).toBe('Hello')
    unmount()
  })

  it('marks live lanes as errored when the stream fails', async () => {
    server.use(
      http.get(
        '/api/comparisons/:id/stream',
        () => new Response(null, { status: 404 }),
      ),
    )
    const { result } = renderHook(() => useArena('missing', ['CLAUDE']))
    await waitFor(() => expect(result.current.state.done).toBe(true))
    expect(result.current.state.lanes.CLAUDE.status).toBe('error')
  })
})

describe('useArena analysis (FR-021)', () => {
  it('lands a replayed analysis event directly in the done phase', async () => {
    server.use(
      http.get('/api/comparisons/:id/stream', () =>
        sseResponse([
          {
            event: 'result',
            data: {
              provider: 'CLAUDE',
              outcome: 'SUCCESS',
              responseText: 'oi',
              errorMessage: null,
              responseTimeMs: 900,
            },
          },
          { event: 'analysis', data: recordedAnalysis },
          { event: 'done', data: { comparisonId: 'c_test', completed: 1 } },
        ]),
      ),
    )
    const { result, unmount } = renderHook(() => useArena('c_test', ['CLAUDE']))
    await waitFor(() => expect(result.current.state.done).toBe(true))
    expect(result.current.state.analysis).toEqual({
      phase: 'done',
      analysis: {
        text: recordedAnalysis.text,
        provider: 'CLAUDE',
        model: 'claude-haiku-4-5',
        labels: { A: 'GEMINI', B: 'CLAUDE' },
      },
    })
    unmount()
  })

  it('streams a requested analysis to completion', async () => {
    server.use(
      http.get('/api/comparisons/:id/analysis/stream', () =>
        sseResponse([
          { event: 'analysis-chunk', data: { delta: '## Cob' } },
          { event: 'analysis-chunk', data: { delta: 'ertura' } },
          { event: 'analysis', data: recordedAnalysis },
          { event: 'done', data: { comparisonId: 'c_test' } },
        ]),
      ),
    )
    const { result, unmount } = renderHook(() => useArena('c_test', ['CLAUDE']))
    await waitFor(() => expect(result.current.state.done).toBe(true))
    act(() => result.current.startAnalysis('CLAUDE', 'claude-haiku-4-5'))
    await waitFor(() =>
      expect(result.current.state.analysis.phase).toBe('done'),
    )
    unmount()
  })

  it('accumulates judge deltas while the analysis streams', async () => {
    // No terminal event: the stream stays in the streaming phase.
    server.use(
      http.get('/api/comparisons/:id/analysis/stream', () =>
        sseResponse([
          { event: 'analysis-chunk', data: { delta: 'Hel' } },
          { event: 'analysis-chunk', data: { delta: 'lo' } },
        ]),
      ),
    )
    const { result, unmount } = renderHook(() => useArena('c_test', ['CLAUDE']))
    await waitFor(() => expect(result.current.state.done).toBe(true))
    act(() => result.current.startAnalysis('CLAUDE', 'claude-haiku-4-5'))
    await waitFor(() =>
      expect(result.current.state.analysis).toEqual({
        phase: 'streaming',
        text: 'Hello',
      }),
    )
    unmount()
  })

  it('flags a rejected judge stream as a retryable error', async () => {
    server.use(
      http.get('/api/comparisons/:id/analysis/stream', () =>
        HttpResponse.json({ error: 'unknown_model' }, { status: 400 }),
      ),
    )
    const { result, unmount } = renderHook(() => useArena('c_test', ['CLAUDE']))
    await waitFor(() => expect(result.current.state.done).toBe(true))
    act(() => result.current.startAnalysis('CLAUDE', 'nope'))
    await waitFor(() =>
      expect(result.current.state.analysis).toEqual({
        phase: 'error',
        message: ANALYSIS_ERROR_MESSAGE,
      }),
    )
    unmount()
  })

  it('supersedes an in-flight judge stream without flagging an error', async () => {
    let calls = 0
    server.use(
      http.get('/api/comparisons/:id/analysis/stream', () => {
        calls += 1
        // First judge hangs forever; the second pick must abort it cleanly.
        if (calls === 1)
          return new HttpResponse(new ReadableStream({ start() {} }), {
            headers: { 'Content-Type': 'text/event-stream' },
          })
        return sseResponse([
          { event: 'analysis', data: recordedAnalysis },
          { event: 'done', data: { comparisonId: 'c_test' } },
        ])
      }),
    )
    const { result, unmount } = renderHook(() => useArena('c_test', ['CLAUDE']))
    await waitFor(() => expect(result.current.state.done).toBe(true))
    act(() => result.current.startAnalysis('GEMINI', 'gemini-2.5-flash'))
    act(() => result.current.startAnalysis('CLAUDE', 'claude-haiku-4-5'))
    await waitFor(() =>
      expect(result.current.state.analysis.phase).toBe('done'),
    )
    // The aborted first stream must not flip the finished analysis to error.
    await act(() => new Promise((resolve) => setTimeout(resolve, 20)))
    expect(result.current.state.analysis.phase).toBe('done')
    unmount()
  })
})
