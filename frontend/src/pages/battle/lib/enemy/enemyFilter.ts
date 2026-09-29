import type { EntityMatcher, Facet } from '@/shared/filter'
import { createEntityMatcher } from '@/shared/filter'
import type { EnemyEntity } from '../../types/EnemyTypes'
import { HIDDEN_BATTLE, type HiddenBattle } from '../battleVocabulary'

export interface EnemyFacetState {
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
