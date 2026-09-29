import { createStore } from 'zustand'
import { DEFAULT_BATTLE_MODE, type BattleCase, type BattleFamily } from '../lib/battleVocabulary'
import { clearEnemyDrillDowns } from './enemyFilterStore'

interface BattleTypeState {
  mode: BattleCase
  entityType: BattleFamily
}

export const battleTypeStore = createStore<BattleTypeState>(() => ({
  mode: DEFAULT_BATTLE_MODE,
  entityType: 'enemy',
}))

export function setBattleType(entityType: BattleFamily) {
  battleTypeStore.setState({ entityType })
}

export function setBattleMode(mode: BattleCase) {
  if (battleTypeStore.getState().mode === mode) return
  clearEnemyDrillDowns()
  battleTypeStore.setState({ mode })
}

export function selectBattleType(state: BattleTypeState): BattleFamily {
  return state.entityType
}

export function selectBattleMode(state: BattleTypeState): BattleCase {
  return state.mode
}
