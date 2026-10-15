import { describe, it, expect } from 'vitest'

import enemySpec from '@static/data/enemySpecList.json'
import assistantSpec from '@static/data/assistantSpecList.json'
import enemyNames from '@static/i18n/EN/enemyNameList.json'
import enemyNamesKR from '@static/i18n/KR/enemyNameList.json'
import sinnerEventSpec from '@static/data/sinnerEventSpecList.json'
import sinnerEventNamesKR from '@static/i18n/KR/sinnerEventNameList.json'
import facetNamesKR from '@static/i18n/KR/battleFacetNames.json'
import type { FilterState } from '@/components/hooks/filterStore'
import { AssistantSpecListSchema } from '../../schemas/AssistantSchemas'
import { BattleFacetNamesSchema } from '../../schemas/BattleSpecSchemas'
import {
  SinnerEventNameListSchema,
  SinnerEventSpecListSchema,
} from '../../schemas/SinnerEventSchemas'
import { EnemyNameListSchema, EnemySpecListSchema } from '../../schemas/EnemySchemas'
import { toAssistantEntity } from '../assistant/assistantEntity'
import { matchesBattle, type BattleFacetState } from '../battleFilter'
import { battleEventTitle, buildBattleSearchTerms, type BattleCardItem } from '../battleListing'
import type { BattleCase, BattleFamily, HiddenBattle } from '../battleVocabulary'
import { toEnemyEntity } from '../enemy/enemyEntity'
import { toSinnerEventEntity } from '../sinnerEvent/sinnerEventEntity'

const ENEMIES = EnemySpecListSchema.parse(enemySpec)
const ASSISTANTS = AssistantSpecListSchema.parse(assistantSpec)
const NAMES = EnemyNameListSchema.parse(enemyNames)
const NAMES_KR = EnemyNameListSchema.parse(enemyNamesKR)

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
  names = NAMES,
): boolean {
  const state: FilterState<BattleFacetState> = { values: { ...EMPTY, ...values }, searchQuery }
  return matchesBattle(
    item,
    state,
    buildBattleSearchTerms(item.entity.id, names, mode, undefined, undefined),
  )
}

describe('matchesBattle', () => {
  it('shows every family when no type is selected and only the selected ones otherwise', () => {
    expect(matches(enemy('1126'), {})).toBe(true)
    expect(matches(assistant('400001'), {})).toBe(true)
    const assistantsOnly = { selectedTypes: new Set<BattleFamily>(['assistant']) }
    expect(matches(enemy('1126'), assistantsOnly)).toBe(false)
    expect(matches(assistant('400001'), assistantsOnly)).toBe(true)
  })

  it('matches any of the selected values within a facet', () => {
    const chapters = new Set(['canto:8', 'intervallo:7.5'])
    expect(matches(enemy('1152'), { selectedChapters: chapters })).toBe(true)
    expect(matches(enemy('1126'), { selectedChapters: chapters })).toBe(true)
    expect(matches(enemy('1307'), { selectedChapters: chapters })).toBe(false)
  })

  it('shows the merged Zilu card under both the 7.5 and the canto 8 filter', () => {
    expect(matches(enemy('1126'), { selectedChapters: new Set(['intervallo:7.5']) })).toBe(true)
    expect(matches(enemy('1126'), { selectedChapters: new Set(['canto:8']) })).toBe(true)
  })

  it('requires every facet with a selection', () => {
    const values = {
      selectedChapters: new Set(['canto:8']),
      selectedThemePacks: new Set(['1023']),
    }
    expect(matches(enemy('1126'), values)).toBe(true)
    expect(matches(enemy('1134'), values)).toBe(false)
  })

  it('matches the hidden battle option only on hidden enemies', () => {
    const values = { selectedHidden: new Set<HiddenBattle>(['hidden']) }
    expect(matches(enemy('1332'), values)).toBe(true)
    expect(matches(enemy('1126'), values)).toBe(false)
  })

  it('matches a railway line by its numeric id', () => {
    expect(matches(enemy('90014'), { selectedRailwayLines: new Set([1001]) })).toBe(true)
    expect(matches(enemy('90014'), { selectedRailwayLines: new Set([2]) })).toBe(false)
  })

  it('matches the search against the name the mode shows', () => {
    expect(matches(enemy('1126'), {}, 'zilu')).toBe(true)
    expect(matches(enemy('1307'), {}, 'zilu')).toBe(false)
    expect(matches(enemy('1152'), {}, 'jia mu', 'story')).toBe(true)
  })

  it('matches every unit name of the card in every mode', () => {
    expect(NAMES['1152']?.aliases).toContain('Jia Mu')
    expect(matches(enemy('1152'), {}, 'jia mu', 'refractionRailway')).toBe(true)
  })
})

describe('name search', () => {
  it('finds the barber card by its masked alias and by its name', () => {
    expect(NAMES_KR['8395']?.aliases).toContain('???')
    expect(matches(enemy('8395'), {}, '???', 'story', NAMES_KR)).toBe(true)
    expect(matches(enemy('8395'), {}, '이발사', 'story', NAMES_KR)).toBe(true)
    expect(matches(enemy('8395'), {}, '이발사', 'refractionRailway', NAMES_KR)).toBe(true)
  })

  it('matches the composed envy name by its identity title', () => {
    const terms = buildBattleSearchTerms(
      '1099',
      NAMES_KR,
      'story',
      'W사 3등급\n정리 요원',
      undefined,
    )
    expect(terms).toContain('질투 죄종? - w사 3등급 정리 요원')
  })
})

describe('sinner event search', () => {
  const specs = SinnerEventSpecListSchema.parse(sinnerEventSpec)
  const names = SinnerEventNameListSchema.parse(sinnerEventNamesKR)
  const chapterNames = BattleFacetNamesSchema.parse(facetNamesKR).chapters
  const t = (key: string) => key

  function found(id: string, searchQuery: string): boolean {
    const entry = Object.entries(specs).find(([key]) => key === id)
    if (entry === undefined) throw new Error(`no sinner event ${id}`)
    const item: BattleCardItem = {
      family: 'sinnerEvent',
      entity: toSinnerEventEntity(id, entry[1]),
    }
    const eventTitle = battleEventTitle('sinnerEvent', entry[1].chapters, chapterNames, t)
    return matchesBattle(
      item,
      { values: EMPTY, searchQuery },
      buildBattleSearchTerms(id, names, 'story', undefined, eventTitle),
    )
  }

  it('finds sinner event cards by their event name', () => {
    expect(found('10101', '한봄')).toBe(true)
    expect(found('400025', '한봄 밤의 꿈 3')).toBe(true)
    expect(found('10101', '한봄 밤의 꿈 3')).toBe(false)
  })
})
