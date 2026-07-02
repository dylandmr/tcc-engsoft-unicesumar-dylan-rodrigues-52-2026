import type {
  AnalysisChunkEvent,
  AnalysisResult,
  ChunkEvent,
  DoneEvent,
  ProviderId,
  ProviderResult,
} from '../types'
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

/**
 * Fetch an SSE endpoint and feed each parsed event block to `dispatch`. Uses
 * `fetch` + a streamed `ReadableStream` body (rather than native EventSource)
 * so the same GET endpoint, cookie auth and event grammar work identically
 * under jsdom + MSW in tests. Cookies ride along via `credentials: 'include'`,
 * matching the contract's session-cookie auth.
 */
async function readEventStream(
  url: string,
  errorCode: string,
  dispatch: (evt: ParsedEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const res = await fetch(url, {
    credentials: 'include',
    headers: { Accept: 'text/event-stream' },
    signal,
  })
  if (!res.ok) throw new ApiError(errorCode, res.status)

  const reader = res.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx: number
    while ((idx = buffer.indexOf('\n\n')) !== -1) {
      const evt = parseBlock(buffer.slice(0, idx))
      if (evt) dispatch(evt)
      buffer = buffer.slice(idx + 2)
    }
  }
}

export interface StreamHandlers {
  onChunk: (chunk: ChunkEvent) => void
  onResult: (result: ProviderResult) => void
  onDone: (done: DoneEvent) => void
  /**
   * Replaying a COMPLETE comparison also re-emits its recorded analysis
   * (FR-021) before `done`. Optional — the event is additive to the
   * contract, so callers that ignore it are unaffected.
   */
  onAnalysis?: (analysis: AnalysisResult) => void
}

/**
 * Open the comparison SSE stream and dispatch each `chunk` / `result` /
 * `analysis` / `done` event as it arrives. Unknown event names are ignored,
 * keeping the stream forward-compatible.
 */
export async function streamComparison(
  comparisonId: string,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  return readEventStream(
    `/api/comparisons/${comparisonId}/stream`,
    'stream_failed',
    (evt) => {
      if (evt.event === 'chunk') handlers.onChunk(evt.data as ChunkEvent)
      else if (evt.event === 'result')
        handlers.onResult(evt.data as ProviderResult)
      else if (evt.event === 'analysis')
        handlers.onAnalysis?.(evt.data as AnalysisResult)
      else if (evt.event === 'done') handlers.onDone(evt.data as DoneEvent)
      // Any other event name is ignored (e.g. keep-alive comments carry no data).
    },
    signal,
  )
}

export interface AnalysisStreamHandlers {
  onChunk: (chunk: AnalysisChunkEvent) => void
  onAnalysis: (analysis: AnalysisResult) => void
}

/**
 * Open the on-demand "key differences" judge stream (FR-021): incremental
 * `analysis-chunk` deltas followed by the terminal `analysis` event. The
 * judge is always the user's explicit pick — provider and model ride as
 * query parameters (FR-020: no default judge). `done` merely closes the
 * stream; unknown events are ignored.
 */
export async function streamAnalysis(
  comparisonId: string,
  judgeProvider: ProviderId,
  judgeModel: string,
  handlers: AnalysisStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const judge = new URLSearchParams({
    provider: judgeProvider,
    model: judgeModel,
  })
  return readEventStream(
    `/api/comparisons/${comparisonId}/analysis/stream?${judge}`,
    'analysis_failed',
    (evt) => {
      if (evt.event === 'analysis-chunk')
        handlers.onChunk(evt.data as AnalysisChunkEvent)
      else if (evt.event === 'analysis')
        handlers.onAnalysis(evt.data as AnalysisResult)
    },
    signal,
  )
}
