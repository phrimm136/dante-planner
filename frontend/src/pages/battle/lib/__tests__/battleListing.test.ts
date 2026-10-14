import { describe, it, expect } from 'vitest'

import enemySpec from '@static/data/enemySpecList.json'
import assistantSpec from '@static/data/assistantSpecList.json'
import { AssistantSpecListSchema } from '../../schemas/AssistantSchemas'
import { EnemySpecListSchema } from '../../schemas/EnemySchemas'
import { toAssistantEntity } from '../assistant/assistantEntity'
import { battleDisplayName, modeItems, spritePlacementStyle } from '../battleListing'
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
      ['enemy-8044', 'enemy-1133', 'enemy-1126', 'enemy-1366'],
      story,
    )
    expect(canto4).toBeGreaterThanOrEqual(0)
    expect(canto4).toBeLessThan(intervallo ?? -1)
    expect(intervallo).toBeLessThan(canto8 ?? -1)
    expect(canto8).toBeLessThan(walpurgis ?? -1)
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
    const [themePack1023, themePack1124] = positions(['enemy-1133', 'enemy-1366'], md)
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

describe('sprite placement', () => {
  it('scales by 45 over the sprite ppu and pins the anchor 35 % down', () => {
    expect(
      spritePlacementStyle({ file: 'a.webp', feetY: 10, ppu: 90, anchorX: 40, anchorY: 20 }),
    ).toMatchObject({
      left: '50%',
      top: '35%',
      marginLeft: '-40px',
      marginTop: '-20px',
      transformOrigin: '40px 20px',
      transform: 'scale(0.5)',
    })
  })

  it('has no placement for a sprite without an anchor', () => {
    expect(spritePlacementStyle({ file: 'a.webp' })).toBeUndefined()
  })
})

describe('card name', () => {
  it('takes the mode name, else the group name, else the group id', () => {
    const entry = { name: 'Heishou', byCase: { story: 'Jia Mu' } }
    expect(battleDisplayName(entry, 'story', '1152')).toBe('Jia Mu')
    expect(battleDisplayName(entry, 'refractionRailway', '1152')).toBe('Heishou')
    expect(battleDisplayName(undefined, 'story', '1152')).toBe('1152')
  })
})
