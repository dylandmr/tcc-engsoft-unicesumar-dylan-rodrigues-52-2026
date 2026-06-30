import { useEffect, useReducer } from 'react'
import type { ProviderId } from '../types'
import { arenaReducer, initArena, type ArenaState } from './arenaReducer'
import { streamComparison } from '../api/sse'

/**
 * Drive a results "arena": open the SSE stream for a comparison, fill each
 * provider lane independently as `result` events arrive, and tick a live
 * latency counter for lanes still in flight.
 */
export function useArena(
  comparisonId: string,
  providers: ProviderId[],
): ArenaState {
  const [state, dispatch] = useReducer(arenaReducer, providers, initArena)

  useEffect(() => {
    const controller = new AbortController()
    const start = Date.now()
    const tick = () =>
      dispatch({ type: 'tick', elapsedMs: Date.now() - start })
    tick()
    const interval = setInterval(tick, 100)

    streamComparison(
      comparisonId,
      {
        onResult: (result) => dispatch({ type: 'result', result }),
        onDone: () => dispatch({ type: 'done' }),
      },
      controller.signal,
    )
      .catch(() => dispatch({ type: 'streamError' }))
      .finally(() => clearInterval(interval))

    return () => {
      clearInterval(interval)
      controller.abort()
    }
  }, [comparisonId])

  return state
}
