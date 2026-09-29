import { createFilterStore } from '@/components/hooks/filterStore'
import type { BattleFamily, HiddenBattle } from '../lib/battleVocabulary'

export const battleFilterStore = createFilterStore({
  selectedTypes: new Set<BattleFamily>(),
  selectedChapters: new Set<string>(),
  selectedThemePacks: new Set<string>(),
  selectedEvents: new Set<string>(),
  selectedHidden: new Set<HiddenBattle>(),
  selectedRailwayLines: new Set<number>(),
  selectedLuxcavations: new Set<string>(),
})

export function clearDrillDowns() {
  battleFilterStore.store.setState((previous) => ({
    values: {
      ...battleFilterStore.store.getInitialState().values,
      selectedTypes: previous.values.selectedTypes,
    },
  }))
}
