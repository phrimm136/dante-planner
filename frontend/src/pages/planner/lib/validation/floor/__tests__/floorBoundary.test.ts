import fs from 'node:fs'
import path from 'node:path'
import { describe, it, expect } from 'vitest'

import { FLOOR_RULE_TABLE, stagesFor } from '@/shared/gameData'
import type { FloorRuleTable } from '@/shared/gameData'
import { renderContentPath } from '../../contentPath'
import { acceptedFloors, admitFloors, parseFloors } from '../floorRules'
import type { Admission, FloorSelectionValue } from '../floorRules'
import type { Violation } from '../../violation'

const pack = (themePackId: string, difficulty = 0, giftIds: unknown = []) => ({
  themePackId,
  difficulty,
  giftIds,
})

const fivePacks = () => ['1001', '1002', '1003', '1004', '1005'].map((id) => pack(id))

const rendered = (violations: readonly Violation[]) =>
  violations.map(({ code, path: violationPath }) => ({
    code,
    path: renderContentPath(violationPath),
  }))

const renderedAdmission = (admission: Admission) =>
  admission.ok
    ? { ...admission, boundaryViolations: rendered(admission.boundaryViolations) }
    : { ...admission, violations: rendered(admission.violations) }

const FIVE_FLOORS = FLOOR_RULE_TABLE.categories['5F']

const accepted = (floor: FloorSelectionValue) => ({ kind: 'accepted', floor })

const rejected = (salvage: FloorSelectionValue) => ({ kind: 'rejected', salvage })

const parse = (raw: unknown) => {
  const parsed = parseFloors(raw, FIVE_FLOORS)
  return { floors: parsed.floors, violations: rendered(parsed.violations) }
}

const violationsOf = (admission: ReturnType<typeof renderedAdmission>) =>
  admission.ok ? [] : admission.violations

describe('parseFloors boundary contract', () => {
  it('reads a null themePackId as no pack', () => {
    expect(parse([{ themePackId: null, difficulty: 0, giftIds: [] }])).toEqual({
      floors: [accepted({ difficulty: 0, giftIds: [] })],
      violations: [],
    })
  })

  it('rejects an empty themePackId as INVALID_FIELD_TYPE and an absent one as missing', () => {
    expect(parse([{ themePackId: '', difficulty: 0, giftIds: [] }])).toEqual({
      floors: [rejected({ difficulty: 0, giftIds: [] })],
      violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].themePackId' }],
    })
    expect(parse([{ difficulty: 0, giftIds: [] }]).violations).toEqual([
      { code: 'MISSING_REQUIRED_FIELD', path: 'floorSelections[0].themePackId' },
    ])
  })

  it('rejects absent giftIds and difficulty as missing required fields', () => {
    expect(parse([{ themePackId: '1001' }])).toEqual({
      floors: [rejected({ themePackId: '1001', giftIds: [] })],
      violations: [
        { code: 'MISSING_REQUIRED_FIELD', path: 'floorSelections[0].difficulty' },
        { code: 'MISSING_REQUIRED_FIELD', path: 'floorSelections[0].giftIds' },
      ],
    })
  })

  it('rejects an unknown floor key at that key', () => {
    expect(parse([{ ...pack('1001'), extra: 1 }]).violations).toEqual([
      { code: 'UNKNOWN_FIELD', path: 'floorSelections[0].extra' },
    ])
  })

  it('rejects non-array floorSelections at floorSelections', () => {
    for (const raw of [{}, 'floors', 5, null]) {
      expect(parse(raw)).toEqual({
        floors: [],
        violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections' }],
      })
    }
    expect(parse(undefined)).toEqual({
      floors: [],
      violations: [{ code: 'MISSING_REQUIRED_FIELD', path: 'floorSelections' }],
    })
  })

  it('rejects a non-object floor at floorSelections[i]', () => {
    for (const floor of [5, 'x', null, []]) {
      expect(parse([pack('1001'), floor])).toEqual({
        floors: [
          accepted({ themePackId: '1001', difficulty: 0, giftIds: [] }),
          rejected({ giftIds: [] }),
        ],
        violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[1]' }],
      })
    }
  })

  it('rejects a non-string themePackId at .themePackId', () => {
    expect(parse([{ themePackId: 1001, difficulty: 0, giftIds: [] }]).violations).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].themePackId' },
    ])
  })

  it('rejects non-array giftIds at .giftIds', () => {
    expect(parse([pack('1001', 0, '9002')]).violations).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].giftIds' },
    ])
  })

  it('rejects null giftIds at .giftIds', () => {
    expect(parse([pack('1001', 0, null)])).toEqual({
      floors: [rejected({ themePackId: '1001', difficulty: 0, giftIds: [] })],
      violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].giftIds' }],
    })
  })

  it('rejects null difficulty at .difficulty', () => {
    expect(parse([{ themePackId: '1001', difficulty: null, giftIds: [] }])).toEqual({
      floors: [rejected({ themePackId: '1001', giftIds: [] })],
      violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].difficulty' }],
    })
  })

  it('rejects each non-string gift element at .giftIds[j]', () => {
    expect(parse([pack('1001', 0, ['9002', 9003, null])]).violations).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].giftIds[1]' },
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].giftIds[2]' },
    ])
  })

  it('rejects a non-integer difficulty at .difficulty and keeps out-of-set integers', () => {
    for (const difficulty of ['1', 1.5, Number.NaN]) {
      expect(parse([{ themePackId: '1001', difficulty, giftIds: [] }])).toEqual({
        floors: [rejected({ themePackId: '1001', giftIds: [] })],
        violations: [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].difficulty' }],
      })
    }
    expect(parse([pack('1001', 7)]).floors).toEqual([
      accepted({ themePackId: '1001', difficulty: 7, giftIds: [] }),
    ])
  })

  it('shape-checks floors at index >= floorCount and leaves them out of the floors', () => {
    const { floors, violations } = parse([...fivePacks(), 5, { giftIds: 7 }])
    expect(violations).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[5]' },
      { code: 'MISSING_REQUIRED_FIELD', path: 'floorSelections[6].difficulty' },
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[6].giftIds' },
      { code: 'MISSING_REQUIRED_FIELD', path: 'floorSelections[6].themePackId' },
    ])
    expect(floors).toHaveLength(5)
  })

  it('parses zero floors to zero floors', () => {
    expect(parse([])).toEqual({ floors: [], violations: [] })
  })

  it('carries the category layout', () => {
    expect(parseFloors([], FLOOR_RULE_TABLE.categories['15F']).layout).toBe(
      FLOOR_RULE_TABLE.categories['15F'],
    )
  })

  it('lists only the accepted floors, in order, as acceptedFloors', () => {
    const parsed = parseFloors(
      [pack('1001'), 5, pack('1003'), { ...pack('1004'), themePackId: 7 }],
      FIVE_FLOORS,
    )
    expect(acceptedFloors(parsed)).toEqual([
      { themePackId: '1001', difficulty: 0, giftIds: [] },
      { themePackId: '1003', difficulty: 0, giftIds: [] },
    ])
  })

  it('salvages a rejected floor field by field, stringifying numeric gift ids', () => {
    expect(parse([{ themePackId: '1004', giftIds: ['9002', 9005] }]).floors).toEqual([
      rejected({ themePackId: '1004', giftIds: ['9002', '9005'] }),
    ])
  })

  it('salvages the pack of a floor whose giftIds is not an array', () => {
    expect(parse([{ themePackId: '1001', giftIds: 5 }]).floors).toEqual([
      rejected({ themePackId: '1001', giftIds: [] }),
    ])
  })

  it('salvages nothing from non-scalar fields', () => {
    expect(
      parse([
        {
          themePackId: { id: '1001' },
          difficulty: 'x',
          giftIds: [true, { id: '9001' }, ['9002']],
        },
      ]).floors,
    ).toEqual([rejected({ giftIds: [] })])
  })

  it('salvages a numeric pack as its text', () => {
    expect(parse([{ themePackId: 1003, difficulty: 'x', giftIds: 5 }]).floors).toEqual([
      rejected({ themePackId: '1003', giftIds: [] }),
    ])
  })
})

describe('admitFloors', () => {
  const admit = (
    raw: unknown,
    category: '5F' | '10F' | '15F',
    stage: 'draft' | 'publish' | 'index',
    table?: FloorRuleTable,
  ) =>
    renderedAdmission(
      admitFloors(parseFloors(raw, FLOOR_RULE_TABLE.categories[category]), stage, table),
    )

  it('short-circuits a boundary failure at draft and publish; index admits a non-object floor empty', () => {
    const raw = [pack('1001'), 5, pack('1003'), pack('1004'), pack('1005')]
    const boundary = [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[1]' }]
    expect(admit(raw, '5F', 'draft')).toEqual({ ok: false, violations: boundary })
    expect(admit(raw, '5F', 'publish')).toEqual({ ok: false, violations: boundary })
    expect(admit(raw, '5F', 'index')).toEqual({
      ok: true,
      floors: [raw[0], { giftIds: [] }, raw[2], raw[3], raw[4]],
      boundaryViolations: boundary,
    })
  })

  it('indexes a rejected object floor as its salvage in floor order; draft and publish short-circuit', () => {
    const raw = [pack('1001'), pack('1004', 0, ['9002', 9005]), pack('1003')]
    const boundary = [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[1].giftIds[1]' }]
    expect(admit(raw, '5F', 'draft')).toEqual({ ok: false, violations: boundary })
    expect(admit(raw, '5F', 'publish')).toEqual({ ok: false, violations: boundary })
    expect(admit(raw, '5F', 'index')).toEqual({
      ok: true,
      floors: [raw[0], { themePackId: '1004', difficulty: 0, giftIds: ['9002', '9005'] }, raw[2]],
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
      { code: 'OUT_OF_ORDER_DIFFICULTY', path: 'floorSelections[1].difficulty' },
      { code: 'DUPLICATE_THEMEPACK', path: 'floorSelections[1].themePackId' },
    ])
  })

  it('runs difficultyInRange where the matrix names it', () => {
    expect(stagesFor('difficultyInRange')).toEqual(['publish'])
    const raw = [pack('1001', 2), ...fivePacks().slice(1)]
    const outOfRange = [{ code: 'OUT_OF_RANGE_DIFFICULTY', path: 'floorSelections[0].difficulty' }]

    expect(admit(raw, '5F', 'draft').ok).toBe(true)
    expect(violationsOf(admit(raw, '5F', 'publish'))).toEqual(outOfRange)

    const moved: FloorRuleTable = {
      ...FLOOR_RULE_TABLE,
      rules: { ...FLOOR_RULE_TABLE.rules, difficultyInRange: ['draft'] },
    }
    expect(violationsOf(admit(raw, '5F', 'draft', moved))).toEqual(outOfRange)
    expect(admit(raw, '5F', 'publish', moved).ok).toBe(true)
  })

  it('refuses an absent difficulty as a missing field at draft and publish', () => {
    const raw = [{ themePackId: '1001', giftIds: [] }, ...fivePacks().slice(1)]
    const missing = [{ code: 'MISSING_REQUIRED_FIELD', path: 'floorSelections[0].difficulty' }]
    expect(violationsOf(admit(raw, '5F', 'draft'))).toEqual(missing)
    expect(violationsOf(admit(raw, '5F', 'publish'))).toEqual(missing)
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
        code: 'OUT_OF_ORDER_DIFFICULTY',
        path: `floorSelections[${i}].difficulty`,
      })),
    )
  })

  it('reports only the entry condition on a 10F NORMAL floor', () => {
    const raw = Array.from({ length: 10 }, (_, i) => pack(`${1001 + i}`, i === 2 ? 0 : 1))
    expect(violationsOf(admit(raw, '10F', 'publish'))).toEqual([
      { code: 'INSUFFICIENT_DIFFICULTY_ENTRY', path: 'floorSelections[2].difficulty' },
    ])
  })

  it('moves the entry floors with the shortest category of the table', () => {
    const shrunk: FloorRuleTable = {
      ...FLOOR_RULE_TABLE,
      categories: {
        ...FLOOR_RULE_TABLE.categories,
        '5F': {
          floorCount: 3,
          difficulties: FLOOR_RULE_TABLE.categories['5F'].difficulties.slice(0, 3),
        },
      },
    }
    const raw = Array.from({ length: 10 }, (_, i) =>
      pack(`${1001 + i}`, i === 2 || i === 3 ? 0 : 1),
    )
    expect(violationsOf(admit(raw, '10F', 'publish', shrunk))).toEqual([
      { code: 'INSUFFICIENT_DIFFICULTY_ENTRY', path: 'floorSelections[2].difficulty' },
      { code: 'OUT_OF_RANGE_DIFFICULTY_NOT_HARD', path: 'floorSelections[3].difficulty' },
    ])
  })

  it('does not count a past-count floor as a repeat', () => {
    const raw = [...fivePacks(), pack('1001')]
    expect(admit(raw, '5F', 'draft').ok).toBe(true)
    expect(admit(raw, '5F', 'publish').ok).toBe(true)
  })

  it('reports a malformed floor at floorCount without admitting it to a rule', () => {
    const boundary = [{ code: 'INVALID_FIELD_TYPE', path: 'floorSelections[5]' }]
    expect(admit([...fivePacks(), 5], '5F', 'publish')).toEqual({
      ok: false,
      violations: boundary,
    })
    expect(admit([...fivePacks(), 5], '5F', 'index')).toEqual({
      ok: true,
      floors: fivePacks(),
      boundaryViolations: boundary,
    })
  })

  it('admits zero floors at draft and index, reports every floor missing at publish', () => {
    expect(admit([], '5F', 'draft')).toEqual({ ok: true, floors: [], boundaryViolations: [] })
    expect(admit([], '5F', 'index')).toEqual({ ok: true, floors: [], boundaryViolations: [] })
    expect(violationsOf(admit([], '5F', 'publish'))).toEqual(
      [0, 1, 2, 3, 4].map((i) => ({
        code: 'INSUFFICIENT_FLOORS',
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
        code: 'INSUFFICIENT_FLOORS',
        path: `floorSelections[${i}]`,
      })),
    )
  })

  it('orders violations by floor number, so floor 5 precedes floor 10', () => {
    const raw = Array.from({ length: 15 }, (_, i) =>
      pack(`${1001 + i}`, i === 5 ? 0 : i === 10 ? 1 : i < 10 ? 1 : 3),
    )
    expect(violationsOf(admit(raw, '15F', 'publish'))).toEqual([
      { code: 'OUT_OF_RANGE_DIFFICULTY_NOT_HARD', path: 'floorSelections[5].difficulty' },
      { code: 'OUT_OF_RANGE_DIFFICULTY_NOT_EXTREME', path: 'floorSelections[10].difficulty' },
    ])
  })

  it('refuses an empty-string pack as a malformed field, not an absent pack', () => {
    expect(violationsOf(admit([pack(''), pack('1002')], '5F', 'draft'))).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'floorSelections[0].themePackId' },
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
    expect(imports).toEqual([
      '@/shared/gameData',
      '@/shared/gameData',
      '../boundary',
      '../contentPath',
      '../contentPath',
      '../contentShape',
      '../contentShape',
      '../violation',
      '../violation',
      '../positions',
    ])
  })
})
