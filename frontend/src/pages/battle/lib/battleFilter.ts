import type { EntityMatcher, Facet } from '@/shared/filter'
import { createEntityMatcher } from '@/shared/filter'
import { HIDDEN_BATTLE, type BattleFamily, type HiddenBattle } from './battleVocabulary'

export interface BattleDrillDownState {
  selectedChapters: ReadonlySet<string>
  selectedThemePacks: ReadonlySet<string>
  selectedEvents: ReadonlySet<string>
  selectedHidden: ReadonlySet<HiddenBattle>
  selectedRailwayLines: ReadonlySet<number>
  selectedLuxcavations: ReadonlySet<string>
}

export interface BattleFacetState extends BattleDrillDownState {
  selectedTypes: ReadonlySet<BattleFamily>
}

interface DrillDownFields {
  chapters: readonly string[]
  themePacks: readonly string[]
  events: readonly string[]
  hidden: boolean
  railwayLines: readonly number[]
  luxcavations: readonly string[]
}

export interface BattleListItem<TEntity extends DrillDownFields = DrillDownFields> {
  family: BattleFamily
  entity: TEntity
}

const HIDDEN_VALUES: readonly HiddenBattle[] = [HIDDEN_BATTLE]
const NO_HIDDEN_VALUES: readonly HiddenBattle[] = []

export const DRILL_DOWN_FACETS: readonly Facet<DrillDownFields, BattleDrillDownState>[] = [
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

function onEntity(
  facet: Facet<DrillDownFields, BattleDrillDownState>,
): Facet<BattleListItem, BattleFacetState> {
  return { sel: facet.sel, get: (item) => facet.get(item.entity), mode: facet.mode }
}

export const BATTLE_LIST_FACETS: readonly Facet<BattleListItem, BattleFacetState>[] = [
  { sel: (s) => s.selectedTypes, get: (item) => item.family, mode: 'any' },
  ...DRILL_DOWN_FACETS.map(onEntity),
]

export const matchesBattle: EntityMatcher<BattleListItem, BattleFacetState> =
  createEntityMatcher(BATTLE_LIST_FACETS)
