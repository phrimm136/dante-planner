import { describe, it, expect } from 'vitest'

import enemy1366 from '@static/data/enemy/1366.json'
import enemy8016 from '@static/data/enemy/8016.json'
import { EnemyDataSchema } from '../../schemas/BattleDetailSchemas'
import { defaultRowIndex, groupSelectorRows, selectorEntries } from '../engagementRows'

const rows1366 = EnemyDataSchema.parse(enemy1366).engagements
const groups1366 = groupSelectorRows(rows1366)
const rows8016 = EnemyDataSchema.parse(enemy8016).engagements
const groups8016 = groupSelectorRows(rows8016)

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

  it('opens on the first row of a linked stage', () => {
    const stage = 'abbattle1-4.json#10412'
    const index = defaultRowIndex(groups8016, 'story', stage)
    expect(index).toBe(rows8016.findIndex((row) => row.stage === stage))
    expect(index).not.toBe(defaultRowIndex(groups8016, 'story'))
  })

  it('opens a linked stage in another mode than the arrival mode', () => {
    const stage = 'mirrordungeon-abbattle5-0.json#2060418'
    const index = defaultRowIndex(groups8016, 'story', stage)
    const row = index === undefined ? undefined : rows8016[index]
    expect([row?.case, row?.stage]).toEqual(['mirrorDungeon', stage])
  })

  it('falls back to the arrival default on a stage the card does not hold', () => {
    expect(defaultRowIndex(groups8016, 'story', 'abbattle1-4.json#1')).toBe(
      defaultRowIndex(groups8016, 'story'),
    )
    expect(defaultRowIndex(groups8016, 'story', undefined)).toBe(0)
  })
})
