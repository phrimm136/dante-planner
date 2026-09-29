import { describe, it, expect } from 'vitest'

import enemySpec from '@static/data/enemySpecList.json'
import assistantSpec from '@static/data/assistantSpecList.json'
import enemyNames from '@static/i18n/EN/enemyNameList.json'
import type { FilterState } from '@/components/hooks/filterStore'
import { AssistantSpecListSchema } from '../../schemas/AssistantSchemas'
import { EnemyNameListSchema, EnemySpecListSchema } from '../../schemas/EnemySchemas'
import { toAssistantEntity } from '../assistant/assistantEntity'
import { matchesBattle, type BattleFacetState } from '../battleFilter'
import { buildBattleSearchTerms, type BattleCardItem } from '../battleListing'
import type { BattleCase, BattleFamily, HiddenBattle } from '../battleVocabulary'
import { toEnemyEntity } from '../enemy/enemyEntity'

const ENEMIES = EnemySpecListSchema.parse(enemySpec)
const ASSISTANTS = AssistantSpecListSchema.parse(assistantSpec)
const NAMES = EnemyNameListSchema.parse(enemyNames)

const EMPTY: BattleFacetState = {
  selectedTypes: new Set<BattleFamily>(),
  selectedChapters: new Set<string>(),
  selectedThemePacks: new Set<string>(),
  selectedEvents: new Set<string>(),
  selectedHidden: new Set<HiddenBattle>(),
  selectedRailwayLines: new Set<number>(),
  selectedLuxcavations: new Set<string>(),
}

function enemy(id: string): BattleCardItem {
  const entry = Object.entries(ENEMIES).find(([key]) => key === id)
  if (entry === undefined) throw new Error(`no enemy ${id}`)
  return { family: 'enemy', entity: toEnemyEntity(id, entry[1]) }
}

function assistant(id: string): BattleCardItem {
  const entry = Object.entries(ASSISTANTS).find(([key]) => key === id)
  if (entry === undefined) throw new Error(`no assistant ${id}`)
  return { family: 'assistant', entity: toAssistantEntity(id, entry[1]) }
}

function matches(
  item: BattleCardItem,
  values: Partial<BattleFacetState>,
  searchQuery = '',
  mode: BattleCase = 'story',
): boolean {
  const state: FilterState<BattleFacetState> = { values: { ...EMPTY, ...values }, searchQuery }
  return matchesBattle(item, state, buildBattleSearchTerms(item.entity.id, NAMES, mode))
}

describe('matchesBattle', () => {
  it('shows every family when no type is selected and only the selected ones otherwise', () => {
    expect(matches(enemy('1133'), {})).toBe(true)
    expect(matches(assistant('400001'), {})).toBe(true)
    const assistantsOnly = { selectedTypes: new Set<BattleFamily>(['assistant']) }
    expect(matches(enemy('1133'), assistantsOnly)).toBe(false)
    expect(matches(assistant('400001'), assistantsOnly)).toBe(true)
  })

  it('matches any of the selected values within a facet', () => {
    const chapters = new Set(['canto:8', 'intervallo:7.5'])
    expect(matches(enemy('1133'), { selectedChapters: chapters })).toBe(true)
    expect(matches(enemy('1126'), { selectedChapters: chapters })).toBe(true)
    expect(matches(enemy('8701'), { selectedChapters: chapters })).toBe(false)
  })

  it('requires every facet with a selection', () => {
    const values = {
      selectedChapters: new Set(['canto:8']),
      selectedThemePacks: new Set(['1023']),
    }
    expect(matches(enemy('1133'), values)).toBe(true)
    expect(matches(enemy('1126'), values)).toBe(false)
  })

  it('matches the hidden battle option only on hidden enemies', () => {
    const values = { selectedHidden: new Set<HiddenBattle>(['hidden']) }
    expect(matches(enemy('1332'), values)).toBe(true)
    expect(matches(enemy('1133'), values)).toBe(false)
  })

  it('matches a railway line by its numeric id', () => {
    expect(matches(enemy('9501'), { selectedRailwayLines: new Set([1001]) })).toBe(true)
    expect(matches(enemy('9501'), { selectedRailwayLines: new Set([1]) })).toBe(false)
  })

  it('matches the search against the name the mode shows', () => {
    expect(matches(enemy('1133'), {}, 'zilu')).toBe(true)
    expect(matches(enemy('8701'), {}, 'zilu')).toBe(false)
    expect(matches(enemy('1152'), {}, 'jia mu', 'story')).toBe(true)
    expect(matches(enemy('1152'), {}, 'jia mu', 'refractionRailway')).toBe(false)
  })
})
