import { HttpResponse } from 'msw'

export interface SseEvent {
  event: string
  data: unknown
}

/** Serialise a single SSE event block per the text/event-stream grammar. */
export function encodeSseEvent({ event, data }: SseEvent): string {
  return `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`
}

/**
 * Build an MSW Response that streams the given SSE events as
 * `text/event-stream`. Events are flushed one microtask apart so consumers
 * observe them arriving independently (mirrors the real fan-out).
 */
export function sseResponse(events: SseEvent[]) {
  const encoder = new TextEncoder()
  const stream = new ReadableStream({
    async start(controller) {
      for (const evt of events) {
        controller.enqueue(encoder.encode(encodeSseEvent(evt)))
        await Promise.resolve()
      }
      controller.close()
    },
  })
  return new HttpResponse(stream, {
    headers: { 'Content-Type': 'text/event-stream' },
  })
}
