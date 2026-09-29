import { createStore } from 'zustand'
import { DEFAULT_BATTLE_MODE, type BattleCase } from '../lib/battleVocabulary'
import { clearDrillDowns } from './battleFilterStore'

interface BattleModeState {
  mode: BattleCase
}

export const battleModeStore = createStore<BattleModeState>(() => ({ mode: DEFAULT_BATTLE_MODE }))

export function setBattleMode(mode: BattleCase) {
  if (battleModeStore.getState().mode === mode) return
  clearDrillDowns()
  battleModeStore.setState({ mode })
}

export function selectBattleMode(state: BattleModeState): BattleCase {
  return state.mode
}
