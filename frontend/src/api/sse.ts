import type { DoneEvent, ProviderResult } from '../types'
import { ApiError } from './client'

export interface ParsedEvent {
  event: string
  data: unknown
}

/**
 * Parse a single SSE event block (the text between blank-line separators)
 * into its event name and JSON data. Returns `null` for blocks that carry no
 * `data:` line (comments / keep-alives).
 */
export function parseBlock(block: string): ParsedEvent | null {
  let event = 'message'
  let dataLine: string | null = null
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLine = line.slice(5).trim()
  }
  if (dataLine === null) return null
  return { event, data: JSON.parse(dataLine) }
}

export interface StreamHandlers {
  onResult: (result: ProviderResult) => void
  onDone: (done: DoneEvent) => void
}

function dispatch(block: string, handlers: StreamHandlers): void {
  const evt = parseBlock(block)
  if (!evt) return
  if (evt.event === 'result') handlers.onResult(evt.data as ProviderResult)
  else if (evt.event === 'done') handlers.onDone(evt.data as DoneEvent)
  // Other events (e.g. token chunks) are ignored by this MVP consumer.
}

/**
 * Open the comparison SSE stream and dispatch each `result` / `done` event as
 * it arrives. Uses `fetch` + a streamed `ReadableStream` body (rather than
 * native EventSource) so the same GET endpoint, cookie auth and event grammar
 * work identically under jsdom + MSW in tests. Cookies ride along via
 * `credentials: 'include'`, matching the contract's session-cookie auth.
 */
export async function streamComparison(
  comparisonId: string,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const res = await fetch(`/api/comparisons/${comparisonId}/stream`, {
    credentials: 'include',
    headers: { Accept: 'text/event-stream' },
    signal,
  })
  if (!res.ok) throw new ApiError('stream_failed', res.status)

  const reader = res.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx: number
    while ((idx = buffer.indexOf('\n\n')) !== -1) {
      dispatch(buffer.slice(0, idx), handlers)
      buffer = buffer.slice(idx + 2)
    }
  }
}
