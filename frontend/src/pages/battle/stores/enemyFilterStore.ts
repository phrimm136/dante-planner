import { createFilterStore } from '@/components/hooks/filterStore'
import type { HiddenBattle } from '../lib/battleVocabulary'

export const enemyFilterStore = createFilterStore({
  selectedChapters: new Set<string>(),
  selectedThemePacks: new Set<string>(),
  selectedEvents: new Set<string>(),
  selectedHidden: new Set<HiddenBattle>(),
  selectedRailwayLines: new Set<number>(),
  selectedLuxcavations: new Set<string>(),
})

export function clearEnemyDrillDowns() {
  enemyFilterStore.store.setState({
    values: enemyFilterStore.store.getInitialState().values,
  })
}
