import { describe, it, expect } from 'vitest'

import enemySpec from '@static/data/enemySpecList.json'
import assistantSpec from '@static/data/assistantSpecList.json'
import enemyNamesKR from '@static/i18n/KR/enemyNameList.json'
import identityNamesKR from '@static/i18n/KR/identityNameList.json'
import { AssistantSpecListSchema } from '../../schemas/AssistantSchemas'
import {
  EnemyNameListSchema,
  EnemySpecListSchema,
  type EnemySpec,
} from '../../schemas/EnemySchemas'
import { toAssistantEntity } from '../assistant/assistantEntity'
import {
  battleDisplayName,
  battleListName,
  envyHeaderNames,
  envyIdentityTitle,
  modeItems,
} from '../battleListing'
import { toEnemyEntity } from '../enemy/enemyEntity'

const ENTITIES = [
  ...Object.entries(EnemySpecListSchema.parse(enemySpec)).map(([id, entry]) => ({
    family: 'enemy' as const,
    entity: toEnemyEntity(id, entry),
  })),
  ...Object.entries(AssistantSpecListSchema.parse(assistantSpec)).map(([id, entry]) => ({
    family: 'assistant' as const,
    entity: toAssistantEntity(id, entry),
  })),
]

function positions(
  keys: readonly string[],
  order: readonly { family: string; entity: { id: string } }[],
): number[] {
  const index = new Map(
    order.map((item, position) => [`${item.family}-${String(item.entity.id)}`, position]),
  )
  return keys.map((key) => index.get(key) ?? -1)
}

describe('per-mode card order', () => {
  it('orders Story by the chapter chain, intervallo 7.5 between canto 4 and canto 8, then walpurgis', () => {
    const story = modeItems(ENTITIES, 'story')
    const [canto4, canto8, intervallo, walpurgis] = positions(
      ['enemy-8044', 'enemy-1152', 'enemy-1126', 'enemy-1366'],
      story,
    )
    expect(canto4).toBeGreaterThanOrEqual(0)
    expect(canto4).toBeLessThan(intervallo ?? -1)
    expect(intervallo).toBeLessThan(canto8 ?? -1)
    expect(canto8).toBeLessThan(walpurgis ?? -1)
  })

  it('interleaves a walpurgis night after the chapter its slot unlocks after', () => {
    const story = modeItems(ENTITIES, 'story')
    const [canto6, walpurgis3, intervallo65] = positions(
      ['enemy-8192', 'enemy-8263', 'enemy-8340'],
      story,
    )
    expect(canto6).toBeGreaterThanOrEqual(0)
    expect(canto6).toBeLessThan(walpurgis3 ?? -1)
    expect(walpurgis3).toBeLessThan(intervallo65 ?? -1)
  })

  it.each([
    ['2', 'enemy-1078', 'enemy-8147', 'enemy-90110'],
    ['3', 'enemy-8193', 'enemy-8263', 'enemy-8340'],
    ['4', 'enemy-8350', 'enemy-8363', 'enemy-90156'],
    ['5', 'enemy-8413', 'enemy-90178', 'enemy-1110'],
    ['6', 'enemy-1156', 'enemy-1223', 'enemy-1277'],
    ['8', 'enemy-1347', 'enemy-1366', 'enemy-1424'],
  ] as const)(
    'sorts walpurgis %s between its anchor chapters: %s < %s < %s',
    (_, before, walpurgis, after) => {
      const story = modeItems(ENTITIES, 'story')
      const [beforeAt, walpurgisAt, afterAt] = positions([before, walpurgis, after], story)
      expect(beforeAt).toBeGreaterThanOrEqual(0)
      expect(beforeAt).toBeLessThan(walpurgisAt ?? -1)
      expect(walpurgisAt).toBeLessThan(afterAt ?? -1)
    },
  )

  it('sorts the merged 자로 card 1126 at its 7.5 part-2 key, after 7.5 node 16 and before canto 8', () => {
    const story = modeItems(ENTITIES, 'story')
    const [intervallo75, jaro, canto8] = positions(
      ['enemy-90183', 'enemy-1126', 'enemy-90203'],
      story,
    )
    expect(intervallo75).toBeGreaterThanOrEqual(0)
    expect(intervallo75).toBeLessThan(jaro ?? -1)
    expect(jaro).toBeLessThan(canto8 ?? -1)
    expect(positions(['enemy-1420', 'enemy-1133'], story)).toEqual([-1, -1])
  })

  it('orders Mirror Dungeon by theme pack id, then stage id', () => {
    const md = modeItems(ENTITIES, 'mirrorDungeon')
    const keys = md.map((item) => item.entity.sortKey.mirrorDungeon ?? [])
    for (const [index, key] of keys.slice(1).entries()) {
      const previous = keys[index] ?? []
      const [pack = 0, stage = 0] = key
      const [previousPack = 0, previousStage = 0] = previous
      expect(pack > previousPack || (pack === previousPack && stage >= previousStage)).toBe(true)
    }
    const [themePack1023, themePack1124] = positions(['enemy-1126', 'enemy-1366'], md)
    expect(themePack1023).toBeLessThan(themePack1124 ?? -1)
  })

  it('lists only the entities the mode engages', () => {
    const lux = modeItems(ENTITIES, 'luxcavation')
    expect(lux.every((item) => item.entity.cases.includes('luxcavation'))).toBe(true)
    expect(lux).toHaveLength(
      ENTITIES.filter((item) => item.entity.cases.includes('luxcavation')).length,
    )
  })

  it('mixes the families of a mode into one order', () => {
    const story = modeItems(ENTITIES, 'story')
    expect(new Set(story.map((item) => item.family))).toEqual(new Set(['enemy', 'assistant']))
    const [assistant] = positions(['assistant-400001'], story)
    expect(assistant).toBeGreaterThan(0)
  })
})

describe('card name', () => {
  it('takes the mode name, else the group name, else the group id', () => {
    const entry = { name: 'Heishou', byCase: { story: 'Jia Mu' }, aliases: [] }
    expect(battleDisplayName(entry, 'story', '1152')).toBe('Jia Mu')
    expect(battleDisplayName(entry, 'refractionRailway', '1152')).toBe('Heishou')
    expect(battleDisplayName(undefined, 'story', '1152')).toBe('1152')
  })
})

describe('envy-sin card names', () => {
  const names = EnemyNameListSchema.parse(enemyNamesKR)
  const specs: Record<string, EnemySpec> = EnemySpecListSchema.parse(enemySpec)
  const titles: Record<string, string> = identityNamesKR
  const title = envyIdentityTitle(specs['1099']?.personalityId, titles)

  it('reads the copied identity 10302 of card 1099', () => {
    expect(specs['1099']?.personalityId).toBe(10302)
    expect(title).toBe('W사 3등급\n정리 요원')
  })

  it('lists 1099 by unit name and identity title on one line per mode', () => {
    expect(battleListName(names['1099'], 'story', '1099', title)).toBe(
      '질투 죄종? - W사 3등급 정리 요원',
    )
    expect(battleListName(names['1099'], 'refractionRailway', '1099', title)).toBe(
      '굴절된 질투 죄종? - W사 3등급 정리 요원',
    )
    expect(battleListName(names['1099'], 'mirrorDungeon', '1099', title)).toBe(
      '굴절된 질투 죄종? - W사 3등급 정리 요원',
    )
  })

  it('keeps a card without a copied identity on its name alone', () => {
    expect(envyIdentityTitle(specs['8395']?.personalityId, titles)).toBeUndefined()
    expect(battleListName(names['8395'], 'refractionRailway', '8395', undefined)).toBe(
      '굴절된 이발사',
    )
  })

  it('heads the detail with the identity title and the unit name above it', () => {
    expect(envyHeaderNames('질투 죄종?', 'W사 3등급\n정리 요원')).toEqual({
      name: 'W사 3등급\n정리 요원',
      abnormalityLine: '질투 죄종?',
    })
  })
})
