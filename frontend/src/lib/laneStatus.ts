import type { LaneState } from '../hooks/arenaReducer'

export interface LaneStatusInfo {
  label: string
  toneClass: string
}

/** Map a lane to its telemetry status line (label + tone colour). */
export function laneStatusInfo(lane: LaneState): LaneStatusInfo {
  if (lane.first)
    return { label: 'first to respond', toneClass: 'text-ignition' }
  switch (lane.status) {
    case 'live':
      return { label: 'live · streaming', toneClass: 'text-ignition' }
    case 'done':
      return { label: 'done', toneClass: 'text-mist' }
    case 'empty':
      return { label: 'empty response', toneClass: 'text-mist' }
    case 'error':
      return { label: 'error', toneClass: 'text-error' }
    case 'timeout':
      return { label: 'timeout', toneClass: 'text-timeout' }
    case 'disabled':
      return { label: 'not configured', toneClass: 'text-mist' }
  }
}
