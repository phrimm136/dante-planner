import fs from 'node:fs'
import path from 'node:path'
import { describe, it, expect } from 'vitest'

import { FLOOR_RULE_TABLE, floorCount, stagesFor } from '@/shared/gameData'
import type { FloorRuleTable } from '@/shared/gameData'
import { admitFloors, parseFloors } from '../floorRules'
import type { Admission } from '../floorRules'

const pack = (themePackId: string, difficulty = 0, giftIds: unknown = []) => ({
  themePackId,
  difficulty,
  giftIds,
})

const fivePacks = () => ['1001', '1002', '1003', '1004', '1005'].map((id) => pack(id))

const violationsOf = (admission: Admission) => (admission.ok ? [] : admission.violations)

describe('parseFloors boundary contract', () => {
  it('reads absent, null and empty themePackId as absent', () => {
    const { floors, violations } = parseFloors(
      [{ giftIds: [] }, { themePackId: null, giftIds: [] }, { themePackId: '', giftIds: [] }],
      5,
    )
    expect(violations).toEqual([])
    expect(floors).toEqual([{ giftIds: [] }, { giftIds: [] }, { giftIds: [] }])
  })

  it('reads absent giftIds as an empty list and absent difficulty as absent', () => {
    expect(parseFloors([{ themePackId: '1001' }], 5)).toEqual({
      floors: [{ themePackId: '1001', giftIds: [] }],
      violations: [],
    })
  })

  it('rejects non-array floorSelections at floorSelections', () => {
    for (const raw of [{}, 'floors', 5, null, undefined]) {
      expect(parseFloors(raw, 5)).toEqual({
        floors: [],
        violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections' }],
      })
    }
  })

  it('rejects a non-object floor at floorSelections[i]', () => {
    for (const floor of [5, 'x', null, []]) {
      expect(parseFloors([pack('1001'), floor], 5)).toEqual({
        floors: [{ themePackId: '1001', difficulty: 0, giftIds: [] }, undefined],
        violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[1]' }],
      })
    }
  })

  it('rejects a non-string themePackId at .themePackId', () => {
    expect(parseFloors([{ themePackId: 1001, giftIds: [] }], 5).violations).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].themePackId' },
    ])
  })

  it('rejects non-array giftIds at .giftIds', () => {
    expect(parseFloors([pack('1001', 0, '9002')], 5).violations).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].giftIds' },
    ])
  })

  it('rejects null giftIds at .giftIds', () => {
    expect(parseFloors([pack('1001', 0, null)], 5)).toEqual({
      floors: [undefined],
      violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].giftIds' }],
    })
  })

  it('rejects null difficulty at .difficulty', () => {
    expect(parseFloors([{ themePackId: '1001', difficulty: null, giftIds: [] }], 5)).toEqual({
      floors: [undefined],
      violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].difficulty' }],
    })
  })

  it('rejects each non-string gift element at .giftIds[j]', () => {
    expect(parseFloors([pack('1001', 0, ['9002', 9003, null])], 5).violations).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].giftIds[1]' },
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].giftIds[2]' },
    ])
  })

  it('rejects a non-integer difficulty at .difficulty and keeps out-of-set integers', () => {
    for (const difficulty of ['1', 1.5, Number.NaN]) {
      expect(parseFloors([{ themePackId: '1001', difficulty, giftIds: [] }], 5)).toEqual({
        floors: [undefined],
        violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].difficulty' }],
      })
    }
    expect(parseFloors([pack('1001', 7)], 5).floors).toEqual([
      { themePackId: '1001', difficulty: 7, giftIds: [] },
    ])
  })

  it('leaves floors at index >= floorCount untouched and unreported', () => {
    const { floors, violations } = parseFloors([...fivePacks(), 5, { giftIds: 7 }], 5)
    expect(violations).toEqual([])
    expect(floors).toHaveLength(5)
  })

  it('parses zero floors to zero floors', () => {
    expect(parseFloors([], 5)).toEqual({ floors: [], violations: [] })
  })
})

describe('admitFloors', () => {
  const admit = (
    raw: unknown,
    category: '5F' | '10F' | '15F',
    stage: 'draft' | 'publish' | 'index',
    table?: FloorRuleTable,
  ) => admitFloors(parseFloors(raw, floorCount(category)), category, stage, table)

  it('short-circuits a boundary failure at draft and publish; index admits the rest', () => {
    const raw = [pack('1001'), 5, pack('1003'), pack('1004'), pack('1005')]
    const boundary = [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[1]' }]
    expect(admit(raw, '5F', 'draft')).toEqual({ ok: false, violations: boundary })
    expect(admit(raw, '5F', 'publish')).toEqual({ ok: false, violations: boundary })
    expect(admit(raw, '5F', 'index')).toEqual({
      ok: true,
      floors: [raw[0], raw[2], raw[3], raw[4]],
      boundaryViolations: boundary,
    })
  })

  it('short-circuits a boundary failure at index once the matrix names a rule there', () => {
    const raw = [pack('1001'), 5, pack('1003'), pack('1004'), pack('1005')]
    const withIndexRule: FloorRuleTable = {
      ...FLOOR_RULE_TABLE,
      rules: { ...FLOOR_RULE_TABLE.rules, difficultyInRange: ['publish', 'index'] },
    }
    expect(admit(raw, '5F', 'index', withIndexRule)).toEqual({
      ok: false,
      violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[1]' }],
    })
  })

  it('reports independent rules together', () => {
    const raw = [
      pack('1001', 1),
      pack('1001', 0),
      pack('1003', 1),
      pack('1004', 1),
      pack('1005', 1),
    ]
    expect(violationsOf(admit(raw, '5F', 'publish'))).toEqual([
      { code: 'INVALID_SEQUENCE', path: 'floorSelections[1].difficulty' },
      { code: 'FLOOR_DUPLICATE_THEME_PACK', path: 'floorSelections[1].themePackId' },
    ])
  })

  it('runs difficultyInRange where the matrix names it', () => {
    expect(stagesFor('difficultyInRange')).toEqual(['publish'])
    const raw = [pack('1001', 2), ...fivePacks().slice(1)]
    const outOfRange = [{ code: 'VALUE_OUT_OF_RANGE', path: 'floorSelections[0].difficulty' }]

    expect(admit(raw, '5F', 'draft').ok).toBe(true)
    expect(violationsOf(admit(raw, '5F', 'publish'))).toEqual(outOfRange)

    const moved: FloorRuleTable = {
      ...FLOOR_RULE_TABLE,
      rules: { ...FLOOR_RULE_TABLE.rules, difficultyInRange: ['draft'] },
    }
    expect(violationsOf(admit(raw, '5F', 'draft', moved))).toEqual(outOfRange)
    expect(admit(raw, '5F', 'publish', moved).ok).toBe(true)
  })

  it('reports absent difficulty at publish only', () => {
    const raw = [{ themePackId: '1001', giftIds: [] }, ...fivePacks().slice(1)]
    expect(admit(raw, '5F', 'draft').ok).toBe(true)
    expect(violationsOf(admit(raw, '5F', 'publish'))).toEqual([
      { code: 'VALUE_OUT_OF_RANGE', path: 'floorSelections[0].difficulty' },
    ])
  })

  it('refuses NORMAL after any earlier HARD, not only adjacent', () => {
    const raw = [
      pack('1001', 1),
      pack('1002', 0),
      pack('1003', 0),
      pack('1004', 0),
      pack('1005', 0),
    ]
    expect(violationsOf(admit(raw, '5F', 'publish'))).toEqual(
      [1, 2, 3, 4].map((i) => ({
        code: 'INVALID_SEQUENCE',
        path: `floorSelections[${i}].difficulty`,
      })),
    )
  })

  it('reports only the range on a 10F NORMAL floor', () => {
    const raw = Array.from({ length: 10 }, (_, i) => pack(`${1001 + i}`, i === 2 ? 0 : 1))
    expect(violationsOf(admit(raw, '10F', 'publish'))).toEqual([
      { code: 'VALUE_OUT_OF_RANGE', path: 'floorSelections[2].difficulty' },
    ])
  })

  it('does not count a past-count floor as a repeat', () => {
    const raw = [...fivePacks(), pack('1001')]
    expect(admit(raw, '5F', 'draft').ok).toBe(true)
    expect(admit(raw, '5F', 'publish').ok).toBe(true)
  })

  it('ignores a malformed floor at floorCount', () => {
    expect(admit([...fivePacks(), 5], '5F', 'publish')).toEqual({
      ok: true,
      floors: fivePacks(),
      boundaryViolations: [],
    })
  })

  it('admits zero floors at draft and index, reports every floor missing at publish', () => {
    expect(admit([], '5F', 'draft')).toEqual({ ok: true, floors: [], boundaryViolations: [] })
    expect(admit([], '5F', 'index')).toEqual({ ok: true, floors: [], boundaryViolations: [] })
    expect(violationsOf(admit([], '5F', 'publish'))).toEqual(
      [0, 1, 2, 3, 4].map((i) => ({
        code: 'FLOOR_MISSING_THEME_PACK',
        path: `floorSelections[${i}]`,
      })),
    )
  })

  it('indexes what a short 15F array holds', () => {
    const raw = Array.from({ length: 12 }, (_, i) => pack(`${1001 + i}`, i < 10 ? 1 : 3))
    const indexed = admit(raw, '15F', 'index')
    expect(indexed.ok && indexed.floors).toHaveLength(12)
    expect(violationsOf(admit(raw, '15F', 'publish'))).toEqual(
      [12, 13, 14].map((i) => ({
        code: 'FLOOR_MISSING_THEME_PACK',
        path: `floorSelections[${i}]`,
      })),
    )
  })

  it('orders violations by floor number, so floor 5 precedes floor 10', () => {
    const raw = Array.from({ length: 15 }, (_, i) =>
      pack(`${1001 + i}`, i === 5 ? 0 : i === 10 ? 1 : i < 10 ? 1 : 3),
    )
    expect(violationsOf(admit(raw, '15F', 'publish'))).toEqual([
      { code: 'VALUE_OUT_OF_RANGE', path: 'floorSelections[5].difficulty' },
      { code: 'VALUE_OUT_OF_RANGE', path: 'floorSelections[10].difficulty' },
    ])
  })

  it('treats an empty-string pack as absent in sequence', () => {
    expect(violationsOf(admit([pack(''), pack('1002')], '5F', 'draft'))).toEqual([
      { code: 'INVALID_SEQUENCE', path: 'floorSelections[1]' },
    ])
  })

  it('reports a non-array raw at every stage', () => {
    const boundary = [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections' }]
    expect(violationsOf(admit({}, '5F', 'draft'))).toEqual(boundary)
    expect(violationsOf(admit({}, '5F', 'publish'))).toEqual(boundary)
    expect(admit({}, '5F', 'index')).toEqual({ ok: true, floors: [], boundaryViolations: boundary })
  })
})

describe('floorRules module', () => {
  it('imports no hooks, stores, components or React', () => {
    const source = fs.readFileSync(
      path.resolve(path.dirname(new URL(import.meta.url).pathname), '../floorRules.ts'),
      'utf-8',
    )
    const imports = [...source.matchAll(/from '([^']+)'/g)].map((match) => match[1])
    expect(imports).toEqual(['@/shared/gameData', '@/shared/gameData'])
  })
})
