import { describe, it, expect } from 'vitest'

import enemySpec from '@static/data/enemySpecList.json'
import enemyNames from '@static/i18n/EN/enemyNameList.json'
import type { FilterState } from '@/components/hooks/filterStore'
import { EnemySpecListSchema } from '../../../schemas/EnemySchemas'
import { buildBattleSearchTerms } from '../../battleListing'
import type { BattleCase, HiddenBattle } from '../../battleVocabulary'
import { toEnemyEntity } from '../enemyEntity'
import { matchesEnemy, withCaseSelection, type EnemyFacetState } from '../enemyFilter'

const SPEC = EnemySpecListSchema.parse(enemySpec)

const EMPTY: EnemyFacetState = {
  selectedCases: new Set<BattleCase>(),
  selectedChapters: new Set<string>(),
  selectedThemePacks: new Set<string>(),
  selectedEvents: new Set<string>(),
  selectedHidden: new Set<HiddenBattle>(),
  selectedRailwayLines: new Set<number>(),
  selectedLuxcavations: new Set<string>(),
}

function matches(id: string, values: Partial<EnemyFacetState>, searchQuery = ''): boolean {
  const entry = Object.entries(SPEC).find(([key]) => key === id)
  if (entry === undefined) throw new Error(`no enemy ${id}`)
  const state: FilterState<EnemyFacetState> = { values: { ...EMPTY, ...values }, searchQuery }
  return matchesEnemy(toEnemyEntity(id, entry[1]), state, buildBattleSearchTerms(id, enemyNames))
}

describe('matchesEnemy', () => {
  it('matches any of the selected values within a facet', () => {
    const cases = new Set<BattleCase>(['luxcavation', 'refractral'])
    expect(matches('8701', { selectedCases: cases })).toBe(true)
    expect(matches('5001', { selectedCases: cases })).toBe(true)
    expect(matches('1016', { selectedCases: cases })).toBe(false)
  })

  it('requires every facet with a selection', () => {
    const values = {
      selectedCases: new Set<BattleCase>(['story']),
      selectedThemePacks: new Set(['1023']),
    }
    expect(matches('1144', values)).toBe(true)
    expect(matches('1016', values)).toBe(false)
  })

  it('matches the hidden battle option only on hidden enemies', () => {
    const values = { selectedHidden: new Set<HiddenBattle>(['hidden']) }
    expect(matches('1332', values)).toBe(true)
    expect(matches('1144', values)).toBe(false)
  })

  it('matches a railway line by its numeric id', () => {
    expect(matches('9501', { selectedRailwayLines: new Set([1001]) })).toBe(true)
    expect(matches('9501', { selectedRailwayLines: new Set([1]) })).toBe(false)
  })

  it('matches the search against the name', () => {
    expect(matches('1144', {}, 'zilu')).toBe(true)
    expect(matches('1016', {}, 'zilu')).toBe(false)
  })
})

describe('withCaseSelection', () => {
  const FULL: EnemyFacetState = {
    selectedCases: new Set<BattleCase>([
      'story',
      'luxcavation',
      'mirrorDungeon',
      'refractionRailway',
      'refractral',
    ]),
    selectedChapters: new Set(['canto:8']),
    selectedThemePacks: new Set(['1023']),
    selectedEvents: new Set(['901063']),
    selectedHidden: new Set<HiddenBattle>(['hidden']),
    selectedRailwayLines: new Set([1001]),
    selectedLuxcavations: new Set(['exp']),
  }

  function without(battleCase: BattleCase): EnemyFacetState {
    const cases = new Set(FULL.selectedCases)
    cases.delete(battleCase)
    return withCaseSelection(FULL, EMPTY, cases)
  }

  it.each([
    ['story', ['selectedChapters']],
    ['luxcavation', ['selectedLuxcavations']],
    ['mirrorDungeon', ['selectedEvents', 'selectedHidden', 'selectedThemePacks']],
    ['refractionRailway', ['selectedRailwayLines']],
    ['refractral', []],
  ] as const)('deselecting %s clears exactly %j', (battleCase, cleared) => {
    const next = without(battleCase)
    const emptied = Object.entries(next)
      .filter(([key, selection]) => key !== 'selectedCases' && selection.size === 0)
      .map(([key]) => key)
      .sort()
    expect(emptied).toEqual([...cleared])
    expect(next.selectedCases.has(battleCase)).toBe(false)
  })

  it('keeps every drill-down when a case is added', () => {
    const cases = new Set<BattleCase>(['story'])
    const start = { ...FULL, selectedCases: cases }
    const next = withCaseSelection(start, EMPTY, new Set<BattleCase>(['story', 'refractral']))
    expect(next.selectedChapters).toBe(FULL.selectedChapters)
    expect(next.selectedThemePacks).toBe(FULL.selectedThemePacks)
  })
})
