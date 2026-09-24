import { createFilterStore } from '@/components/hooks/filterStore'
import type { BattleCase, HiddenBattle } from '../lib/battleVocabulary'
import { withCaseSelection } from '../lib/enemy/enemyFilter'

export const enemyFilterStore = createFilterStore({
  selectedCases: new Set<BattleCase>(),
  selectedChapters: new Set<string>(),
  selectedThemePacks: new Set<string>(),
  selectedEvents: new Set<string>(),
  selectedHidden: new Set<HiddenBattle>(),
  selectedRailwayLines: new Set<number>(),
  selectedLuxcavations: new Set<string>(),
})

export function setEnemyCases(cases: Set<BattleCase>) {
  enemyFilterStore.store.setState((prev) => ({
    values: withCaseSelection(prev.values, enemyFilterStore.store.getInitialState().values, cases),
  }))
}
