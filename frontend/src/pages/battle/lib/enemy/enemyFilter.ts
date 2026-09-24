import type { EntityMatcher, Facet } from '@/shared/filter'
import { createEntityMatcher } from '@/shared/filter'
import type { EnemyEntity } from '../../types/EnemyTypes'
import {
  BATTLE_CASES,
  HIDDEN_BATTLE,
  type BattleCase,
  type HiddenBattle,
} from '../battleVocabulary'

export interface EnemyFacetState {
  selectedCases: ReadonlySet<BattleCase>
  selectedChapters: ReadonlySet<string>
  selectedThemePacks: ReadonlySet<string>
  selectedEvents: ReadonlySet<string>
  selectedHidden: ReadonlySet<HiddenBattle>
  selectedRailwayLines: ReadonlySet<number>
  selectedLuxcavations: ReadonlySet<string>
}

const HIDDEN_VALUES: readonly HiddenBattle[] = [HIDDEN_BATTLE]
const NO_HIDDEN_VALUES: readonly HiddenBattle[] = []

export const ENEMY_FACETS: readonly Facet<EnemyEntity, EnemyFacetState>[] = [
  { sel: (s) => s.selectedCases, get: (e) => e.cases, mode: 'any' },
  { sel: (s) => s.selectedChapters, get: (e) => e.chapters, mode: 'any' },
  { sel: (s) => s.selectedThemePacks, get: (e) => e.themePacks, mode: 'any' },
  { sel: (s) => s.selectedEvents, get: (e) => e.events, mode: 'any' },
  {
    sel: (s) => s.selectedHidden,
    get: (e) => (e.hidden ? HIDDEN_VALUES : NO_HIDDEN_VALUES),
    mode: 'any',
  },
  { sel: (s) => s.selectedRailwayLines, get: (e) => e.railwayLines, mode: 'any' },
  { sel: (s) => s.selectedLuxcavations, get: (e) => e.luxcavations, mode: 'any' },
]

export const matchesEnemy: EntityMatcher<EnemyEntity, EnemyFacetState> =
  createEntityMatcher(ENEMY_FACETS)

type DrillDownKey = Exclude<keyof EnemyFacetState, 'selectedCases'>

const CASE_DRILL_DOWNS: Readonly<Record<BattleCase, readonly DrillDownKey[]>> = {
  story: ['selectedChapters'],
  luxcavation: ['selectedLuxcavations'],
  mirrorDungeon: ['selectedThemePacks', 'selectedEvents', 'selectedHidden'],
  refractionRailway: ['selectedRailwayLines'],
  refractral: [],
}

function copyKey<T, K extends keyof T>(target: T, source: T, key: K) {
  target[key] = source[key]
}

export function withCaseSelection<T extends EnemyFacetState>(
  values: T,
  empty: T,
  cases: T['selectedCases'],
): T {
  const next: T = { ...values, selectedCases: cases }
  for (const battleCase of BATTLE_CASES) {
    if (!values.selectedCases.has(battleCase) || cases.has(battleCase)) continue
    for (const key of CASE_DRILL_DOWNS[battleCase]) copyKey(next, empty, key)
  }
  return next
}
