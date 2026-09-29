import { describe, it, expect } from 'vitest'

import enemySpec from '@static/data/enemySpecList.json'
import { EnemySpecListSchema } from '../../schemas/EnemySchemas'
import { battleDisplayName, modeItems, spritePlacementStyle } from '../battleListing'
import { toEnemyEntity } from '../enemy/enemyEntity'

const ENTITIES = Object.entries(EnemySpecListSchema.parse(enemySpec)).map(([id, entry]) =>
  toEnemyEntity(id, entry),
)

function positions(ids: readonly string[], order: readonly { id: string }[]): number[] {
  const index = new Map(order.map((item, position) => [String(item.id), position]))
  return ids.map((id) => index.get(id) ?? -1)
}

describe('per-mode card order', () => {
  it('orders Story canto, then intervallo, then walpurgis, each by chapter and node', () => {
    const story = modeItems(ENTITIES, 'story')
    const [canto4, canto8, intervallo, walpurgis] = positions(
      ['8044', '1133', '1126', '1366'],
      story,
    )
    expect(canto4).toBeGreaterThanOrEqual(0)
    expect(canto4).toBeLessThan(canto8 ?? -1)
    expect(canto8).toBeLessThan(intervallo ?? -1)
    expect(intervallo).toBeLessThan(walpurgis ?? -1)
  })

  it('orders Mirror Dungeon by theme pack id, then stage id', () => {
    const md = modeItems(ENTITIES, 'mirrorDungeon')
    const keys = md.map((item) => item.sortKey.mirrorDungeon ?? [])
    for (const [index, key] of keys.slice(1).entries()) {
      const previous = keys[index] ?? []
      const [pack = 0, stage = 0] = key
      const [previousPack = 0, previousStage = 0] = previous
      expect(pack > previousPack || (pack === previousPack && stage >= previousStage)).toBe(true)
    }
    const [themePack1023, themePack1124] = positions(['1133', '1366'], md)
    expect(themePack1023).toBeLessThan(themePack1124 ?? -1)
  })

  it('lists only the entities the mode engages', () => {
    const lux = modeItems(ENTITIES, 'luxcavation')
    expect(lux.every((item) => item.cases.includes('luxcavation'))).toBe(true)
    expect(lux).toHaveLength(ENTITIES.filter((item) => item.cases.includes('luxcavation')).length)
  })
})

describe('sprite placement', () => {
  it('scales by 53 over the sprite ppu and pins the anchor', () => {
    expect(
      spritePlacementStyle({ file: 'a.webp', feetY: 10, ppu: 106, anchorX: 40, anchorY: 20 }),
    ).toMatchObject({
      left: '50%',
      top: '15%',
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
