import type { Outcome, ProviderId, ProviderResult } from '../types'

export type LaneStatus =
  'live' | 'done' | 'empty' | 'error' | 'timeout' | 'disabled'

/** A provider with no API key configured reports this as its error message. */
export const NOT_CONFIGURED = 'provider_not_configured'

export interface LaneState {
  provider: ProviderId
  status: LaneStatus
  text: string
  errorMessage: string | null
  responseTimeMs: number | null
  elapsedMs: number
  /** "First to respond" badge — the fastest lane to return a real answer. */
  first: boolean
}

export interface ArenaState {
  order: ProviderId[]
  lanes: Record<ProviderId, LaneState>
  done: boolean
  firstAssigned: boolean
}

const OUTCOME_STATUS: Record<Outcome, LaneStatus> = {
  SUCCESS: 'done',
  EMPTY: 'empty',
  ERROR: 'error',
  TIMEOUT: 'timeout',
}

export type ArenaAction =
  | { type: 'tick'; elapsedMs: number }
  | { type: 'result'; result: ProviderResult }
  | { type: 'streamError' }
  | { type: 'done' }

/** Build the initial all-live lane state for the selected providers. */
export function initArena(providers: ProviderId[]): ArenaState {
  const lanes = {} as Record<ProviderId, LaneState>
  for (const provider of providers) {
    lanes[provider] = {
      provider,
      status: 'live',
      text: '',
      errorMessage: null,
      responseTimeMs: null,
      elapsedMs: 0,
      first: false,
    }
  }
  return { order: providers, lanes, done: false, firstAssigned: false }
}

function patchLane(
  state: ArenaState,
  provider: ProviderId,
  patch: Partial<LaneState>,
): ArenaState {
  return {
    ...state,
    lanes: {
      ...state.lanes,
      [provider]: { ...state.lanes[provider], ...patch },
    },
  }
}

export function arenaReducer(
  state: ArenaState,
  action: ArenaAction,
): ArenaState {
  switch (action.type) {
    case 'tick': {
      const lanes = {} as Record<ProviderId, LaneState>
      for (const id of state.order) {
        const lane = state.lanes[id]
        lanes[id] =
          lane.status === 'live'
            ? { ...lane, elapsedMs: action.elapsedMs }
            : lane
      }
      return { ...state, lanes }
    }
    case 'result': {
      const { provider, outcome, responseText, errorMessage, responseTimeMs } =
        action.result
      const responded = outcome === 'SUCCESS' || outcome === 'EMPTY'
      const first = responded && !state.firstAssigned
      // A missing API key is a configuration state, not a failure — surface it
      // as a dimmed "disabled" lane rather than a red error.
      const status =
        errorMessage === NOT_CONFIGURED ? 'disabled' : OUTCOME_STATUS[outcome]
      return patchLane(
        { ...state, firstAssigned: state.firstAssigned || first },
        provider,
        {
          status,
          text: responseText ?? '',
          errorMessage,
          responseTimeMs,
          elapsedMs: responseTimeMs ?? state.lanes[provider].elapsedMs,
          first,
        },
      )
    }
    case 'streamError': {
      const lanes = {} as Record<ProviderId, LaneState>
      for (const id of state.order) {
        const lane = state.lanes[id]
        lanes[id] =
          lane.status === 'live'
            ? { ...lane, status: 'error', errorMessage: 'stream_failed' }
            : lane
      }
      return { ...state, lanes, done: true }
    }
    case 'done':
      return { ...state, done: true }
  }
}
