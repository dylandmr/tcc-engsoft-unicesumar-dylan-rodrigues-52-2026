/** Format a latency in ms as a mono telemetry readout, e.g. 1840 → "1.84s". */
export function formatLatency(ms: number): string {
  return `${(ms / 1000).toFixed(2)}s`
}

/** Rough token estimate (whitespace-delimited words) for lane telemetry. */
export function countTokens(text: string): number {
  const trimmed = text.trim()
  return trimmed === '' ? 0 : trimmed.split(/\s+/).length
}

/** Coarse relative time for the history list, e.g. "2 min ago", "yesterday". */
export function relativeTime(iso: string, now: number = Date.now()): string {
  const seconds = Math.floor((now - new Date(iso).getTime()) / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return hours === 1 ? '1 hour ago' : `${hours} hours ago`
  const days = Math.floor(hours / 24)
  return days === 1 ? 'yesterday' : `${days} days ago`
}
