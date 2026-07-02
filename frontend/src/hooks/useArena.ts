import { useCallback, useEffect, useReducer, useRef } from 'react'
import type { ProviderId } from '../types'
import { arenaReducer, initArena, type ArenaState } from './arenaReducer'
import { streamAnalysis, streamComparison } from '../api/sse'

export interface Arena {
  state: ArenaState
  /**
   * Open the key-differences judge stream (FR-021) with the user-picked
   * judge (FR-020: no default). Only one analysis stream runs at a time — a
   * new request supersedes (aborts) the previous one, and navigation/unmount
   * closes it.
   */
  startAnalysis: (judgeProvider: ProviderId, judgeModel: string) => void
}

/**
 * Drive a results "arena": open the SSE stream for a comparison, fill each
 * provider lane independently as `result` events arrive, and tick a live
 * latency counter for lanes still in flight.
 */
export function useArena(comparisonId: string, providers: ProviderId[]): Arena {
  const [state, dispatch] = useReducer(arenaReducer, providers, initArena)
  const analysisAbort = useRef<AbortController | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    const start = Date.now()
    const tick = () => dispatch({ type: 'tick', elapsedMs: Date.now() - start })
    tick()
    const interval = setInterval(tick, 100)

    streamComparison(
      comparisonId,
      {
        onChunk: (chunk) => dispatch({ type: 'chunk', chunk }),
        onResult: (result) => dispatch({ type: 'result', result }),
        // Replay: a COMPLETE comparison re-emits its recorded analysis before
        // `done` — it lands directly in the `done` phase (FR-021).
        onAnalysis: (analysis) =>
          dispatch({ type: 'analysisResult', analysis }),
        onDone: () => dispatch({ type: 'done' }),
      },
      controller.signal,
    )
      .catch(() => dispatch({ type: 'streamError' }))
      .finally(() => clearInterval(interval))

    return () => {
      clearInterval(interval)
      controller.abort()
      analysisAbort.current?.abort()
    }
  }, [comparisonId])

  const startAnalysis = useCallback(
    (judgeProvider: ProviderId, judgeModel: string) => {
      // Only one judge stream at a time — a new pick supersedes the previous.
      analysisAbort.current?.abort()
      const controller = new AbortController()
      analysisAbort.current = controller
      dispatch({ type: 'analysisStart' })
      streamAnalysis(
        comparisonId,
        judgeProvider,
        judgeModel,
        {
          onChunk: (chunk) =>
            dispatch({ type: 'analysisChunk', delta: chunk.delta }),
          onAnalysis: (analysis) =>
            dispatch({ type: 'analysisResult', analysis }),
        },
        controller.signal,
      ).catch(() => {
        // An abort (unmount / superseded request) is not a judge failure.
        if (!controller.signal.aborted) dispatch({ type: 'analysisError' })
      })
    },
    [comparisonId],
  )

  return { state, startAnalysis }
}
