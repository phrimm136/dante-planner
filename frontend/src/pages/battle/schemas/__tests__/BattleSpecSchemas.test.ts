import { describe, expect, it } from 'vitest'

import enemySpec from '@static/data/enemySpecList.json'
import enemyNamesKR from '@static/i18n/KR/enemyNameList.json'
import {
  BattleNameEntrySchema,
  BattleSpriteSchema,
  SpritePlacementSchema,
} from '../BattleSpecSchemas'
import { EnemyNameListSchema, EnemySpecListSchema, type EnemySpec } from '../EnemySchemas'

const BRAZEN_BULL = {
  file: '8041_BrassBullAppearance.webp',
  feetY: 992.97,
  ppu: 240,
  width: 1946,
  height: 1120,
  anchorX: 973.74,
  massBox: [232, 137, 1611, 934],
  centroid: [909.57, 501.28],
  drawnBox: [10, 10, 1890, 1111],
  anchorY: -112.23,
}

describe('battle spec entries', () => {
  const specs: Record<string, EnemySpec> = EnemySpecListSchema.parse(enemySpec)

  it('reads the sprite measures of Brazen Bull 8041', () => {
    expect(specs['8041']?.sprite).toEqual(BRAZEN_BULL)
  })

  it('reads the copied identity of envy-sin card 1099', () => {
    expect(specs['1099']?.personalityId).toBe(10302)
    expect(specs['8041']?.personalityId).toBeUndefined()
  })

  it('rejects a placed sprite without its measures', () => {
    const { massBox: _massBox, ...unmeasured } = BRAZEN_BULL
    expect(SpritePlacementSchema.safeParse(unmeasured).success).toBe(false)
    expect(BattleSpriteSchema.safeParse(unmeasured).success).toBe(false)
  })

  it('reads a sprite that ships only its file', () => {
    expect(BattleSpriteSchema.parse({ file: '9999.webp' })).toEqual({ file: '9999.webp' })
  })
})

describe('battle name entries', () => {
  it('reads every other unit name of the barber card as an alias', () => {
    expect(EnemyNameListSchema.parse(enemyNamesKR)['8395']?.aliases).toEqual([
      '???',
      '굴절된 이발사',
    ])
  })

  it('requires the alias list', () => {
    expect(BattleNameEntrySchema.safeParse({ name: '이발사' }).success).toBe(false)
    expect(BattleNameEntrySchema.parse({ name: '이발사', aliases: [] }).aliases).toEqual([])
  })
})
