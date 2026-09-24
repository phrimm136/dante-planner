import { createStore } from 'zustand'
import type { BattleFamily } from '../lib/battleVocabulary'

interface BattleTypeState {
  entityType: BattleFamily
}

export const battleTypeStore = createStore<BattleTypeState>(() => ({ entityType: 'enemy' }))

export function setBattleType(entityType: BattleFamily) {
  battleTypeStore.setState({ entityType })
}

export function selectBattleType(state: BattleTypeState): BattleFamily {
  return state.entityType
}
