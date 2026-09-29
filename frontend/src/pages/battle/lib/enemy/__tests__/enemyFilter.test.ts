import { describe, it, expect } from 'vitest'

import enemySpec from '@static/data/enemySpecList.json'
import enemyNames from '@static/i18n/EN/enemyNameList.json'
import type { FilterState } from '@/components/hooks/filterStore'
import { EnemyNameListSchema, EnemySpecListSchema } from '../../../schemas/EnemySchemas'
import { buildBattleSearchTerms } from '../../battleListing'
import type { BattleCase, HiddenBattle } from '../../battleVocabulary'
import { toEnemyEntity } from '../enemyEntity'
import { matchesEnemy, type EnemyFacetState } from '../enemyFilter'

const SPEC = EnemySpecListSchema.parse(enemySpec)
const NAMES = EnemyNameListSchema.parse(enemyNames)

const EMPTY: EnemyFacetState = {
  selectedChapters: new Set<string>(),
  selectedThemePacks: new Set<string>(),
  selectedEvents: new Set<string>(),
  selectedHidden: new Set<HiddenBattle>(),
  selectedRailwayLines: new Set<number>(),
  selectedLuxcavations: new Set<string>(),
}

function matches(
  id: string,
  values: Partial<EnemyFacetState>,
  searchQuery = '',
  mode: BattleCase = 'story',
): boolean {
  const entry = Object.entries(SPEC).find(([key]) => key === id)
  if (entry === undefined) throw new Error(`no enemy ${id}`)
  const state: FilterState<EnemyFacetState> = { values: { ...EMPTY, ...values }, searchQuery }
  return matchesEnemy(toEnemyEntity(id, entry[1]), state, buildBattleSearchTerms(id, NAMES, mode))
}

describe('matchesEnemy', () => {
  it('matches any of the selected values within a facet', () => {
    const chapters = new Set(['canto:8', 'intervallo:7.5'])
    expect(matches('1133', { selectedChapters: chapters })).toBe(true)
    expect(matches('1126', { selectedChapters: chapters })).toBe(true)
    expect(matches('8701', { selectedChapters: chapters })).toBe(false)
  })

  it('requires every facet with a selection', () => {
    const values = {
      selectedChapters: new Set(['canto:8']),
      selectedThemePacks: new Set(['1023']),
    }
    expect(matches('1133', values)).toBe(true)
    expect(matches('1126', values)).toBe(false)
  })

  it('matches the hidden battle option only on hidden enemies', () => {
    const values = { selectedHidden: new Set<HiddenBattle>(['hidden']) }
    expect(matches('1332', values)).toBe(true)
    expect(matches('1133', values)).toBe(false)
  })

  it('matches a railway line by its numeric id', () => {
    expect(matches('9501', { selectedRailwayLines: new Set([1001]) })).toBe(true)
    expect(matches('9501', { selectedRailwayLines: new Set([1]) })).toBe(false)
  })

  it('matches the search against the name the mode shows', () => {
    expect(matches('1133', {}, 'zilu')).toBe(true)
    expect(matches('8701', {}, 'zilu')).toBe(false)
    expect(matches('1152', {}, 'jia mu', 'story')).toBe(true)
    expect(matches('1152', {}, 'jia mu', 'refractionRailway')).toBe(false)
  })
})
