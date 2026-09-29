import { describe, it, expect } from 'vitest'

import enemy1366 from '@static/data/enemy/1366.json'
import enemy8390 from '@static/data/enemy/8390.json'
import { EnemyDataSchema } from '../../schemas/BattleDetailSchemas'
import { defaultRowIndex, firstMapName, groupSelectorRows } from '../engagementRows'

const rows1366 = EnemyDataSchema.parse(enemy1366).engagements
const groups1366 = groupSelectorRows(rows1366)

describe('selector rows', () => {
  it('groups rows by mode in the list order, keeping file order inside a mode', () => {
    expect(groups1366.map((group) => group.battleCase)).toEqual(['story', 'mirrorDungeon'])
    const story = groups1366[0]?.rows.map(({ index }) => index) ?? []
    expect(story).toEqual([...story].sort((a, b) => a - b))
  })

  it('numbers the units of one stage in row order and leaves single-unit stages bare', () => {
    const stage10570 = groups1366[0]?.rows.filter(({ row }) => row.stage.endsWith('#10570')) ?? []
    expect(stage10570.map(({ row, suffix }) => [row.unit, suffix])).toEqual([
      [1366, 1],
      [1366, 1],
      [1367, 2],
      [1367, 2],
      [1368, 3],
      [1368, 3],
    ])
    const single = groupSelectorRows(EnemyDataSchema.parse(enemy8390).engagements)
    expect(single.flatMap((group) => group.rows.map(({ suffix }) => suffix))).toEqual([
      undefined,
      undefined,
    ])
  })

  it('opens on the first row of the arrival mode, else the first row', () => {
    const md = defaultRowIndex(groups1366, 'mirrorDungeon')
    expect(md === undefined ? undefined : rows1366[md]?.case).toBe('mirrorDungeon')
    expect(defaultRowIndex(groups1366, 'story')).toBe(0)
    expect(defaultRowIndex(groups1366, 'refractral')).toBe(0)
    expect(defaultRowIndex([], 'story')).toBeUndefined()
  })

  it('takes the background from the first row in mode order', () => {
    expect(firstMapName(groups1366)).toBe('Walpu8_WelfareHallway')
    expect(firstMapName([])).toBeUndefined()
  })
})
