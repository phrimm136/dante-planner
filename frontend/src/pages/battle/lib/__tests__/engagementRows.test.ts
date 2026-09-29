import { describe, it, expect } from 'vitest'

import enemy1366 from '@static/data/enemy/1366.json'
import { EnemyDataSchema } from '../../schemas/BattleDetailSchemas'
import {
  defaultRowIndex,
  firstMapName,
  groupSelectorRows,
  selectorEntries,
} from '../engagementRows'

const rows1366 = EnemyDataSchema.parse(enemy1366).engagements
const groups1366 = groupSelectorRows(rows1366)

describe('selector rows', () => {
  it('groups rows by mode in the list order, keeping file order inside a mode', () => {
    expect(groups1366.map((group) => group.battleCase)).toEqual(['story', 'mirrorDungeon'])
    const story = groups1366[0]?.rows.map(({ index }) => index) ?? []
    expect(story).toEqual([...story].sort((a, b) => a - b))
  })

  it('collapses rows whose label and content match and numbers those whose content differs', () => {
    const rows = [
      { index: 0, label: 'A', content: 'x' },
      { index: 1, label: 'A', content: 'x' },
      { index: 2, label: 'A', content: 'y' },
      { index: 3, label: 'B', content: 'x' },
    ]
    const story = groups1366[0]?.rows.slice(0, 4) ?? []
    const specs = new Map(story.map((each, position) => [each.row, rows[position]]))
    const entries = selectorEntries(
      story,
      (row) => specs.get(row)?.label ?? '',
      (row) => specs.get(row)?.content ?? '',
    )
    expect(entries.map(({ index, label, suffix }) => [index, label, suffix])).toEqual([
      [story[0]?.index, 'A', 1],
      [story[2]?.index, 'A', 2],
      [story[3]?.index, 'B', undefined],
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
