import type { LaneState } from '../hooks/arenaReducer'

export interface LaneStatusInfo {
  label: string
  toneClass: string
}

/** Map a lane to its telemetry status line (label + tone colour). */
export function laneStatusInfo(lane: LaneState): LaneStatusInfo {
  if (lane.first)
    return { label: 'primeiro a responder', toneClass: 'text-ignition' }
  switch (lane.status) {
    case 'live':
      return { label: 'ao vivo · transmitindo', toneClass: 'text-ignition' }
    case 'done':
      return { label: 'concluído', toneClass: 'text-mist' }
    case 'empty':
      return { label: 'resposta vazia', toneClass: 'text-mist' }
    case 'error':
      return { label: 'erro', toneClass: 'text-error' }
    case 'timeout':
      return { label: 'tempo esgotado', toneClass: 'text-timeout' }
    case 'disabled':
      return { label: 'não configurado', toneClass: 'text-mist' }
  }
}
