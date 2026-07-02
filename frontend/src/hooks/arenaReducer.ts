import type { ChunkEvent, Outcome, ProviderId, ProviderResult } from '../types'

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
  /** Time-to-first-token in ms, same clock as responseTimeMs (FR-019). */
  firstTokenMs: number | null
  /** Provider-reported prompt token count (FR-019). */
  inputTokens: number | null
  /** Provider-reported completion token count (FR-019). */
  outputTokens: number | null
  /** Exact model id the provider reports as having answered (FR-019). */
  model: string | null
  elapsedMs: number
  /**
   * "First to respond" badge — the fastest lane by persisted responseTimeMs.
   * Never set by the reducer (SSE arrival order lies on history replay);
   * the arena derives it via buildRaceSummary and injects it per render.
   */
  first: boolean
}

export interface ArenaState {
  order: ProviderId[]
  lanes: Record<ProviderId, LaneState>
  done: boolean
}

const OUTCOME_STATUS: Record<Outcome, LaneStatus> = {
  SUCCESS: 'done',
  EMPTY: 'empty',
  ERROR: 'error',
  TIMEOUT: 'timeout',
}

export type ArenaAction =
  | { type: 'tick'; elapsedMs: number }
  | { type: 'chunk'; chunk: ChunkEvent }
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
      firstTokenMs: null,
      inputTokens: null,
      outputTokens: null,
      model: null,
      elapsedMs: 0,
      first: false,
    }
  }
  return { order: providers, lanes, done: false }
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
    case 'chunk': {
      // Append the streamed delta to the lane's growing text (live token streaming).
      const { provider, delta } = action.chunk
      return patchLane(state, provider, {
        text: state.lanes[provider].text + delta,
      })
    }
    case 'result': {
      const { provider, outcome, responseText, errorMessage, responseTimeMs } =
        action.result
      // A missing API key is a configuration state, not a failure — surface it
      // as a dimmed "disabled" lane rather than a red error.
      const status =
        errorMessage === NOT_CONFIGURED ? 'disabled' : OUTCOME_STATUS[outcome]
      return patchLane(state, provider, {
        status,
        text: responseText ?? '',
        errorMessage,
        responseTimeMs,
        firstTokenMs: action.result.firstTokenMs ?? null,
        inputTokens: action.result.inputTokens ?? null,
        outputTokens: action.result.outputTokens ?? null,
        model: action.result.model ?? null,
        elapsedMs: responseTimeMs ?? state.lanes[provider].elapsedMs,
      })
    }
    case 'streamError': {
      const lanes = {} as Record<ProviderId, LaneState>
      for (const id of state.order) {
        const lane = state.lanes[id]
        lanes[id] =
          lane.status === 'live'
            ? {
                ...lane,
                status: 'error',
                errorMessage: 'Falha na transmissão.',
              }
            : lane
      }
      return { ...state, lanes, done: true }
    }
    case 'done':
      return { ...state, done: true }
  }
}
