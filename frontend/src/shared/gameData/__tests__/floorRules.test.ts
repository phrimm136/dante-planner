import { describe, it, expect } from 'vitest'

import {
  FLOOR_RULE_TABLE,
  FloorRuleTableSchema,
  allowedDifficulties,
  floorCount,
  stagesFor,
} from '../floorRules'

const VALID_FILE = {
  schemaVersion: 1,
  stages: ['draft', 'publish', 'index'],
  categories: {
    '5F': { floorCount: 5, difficulties: Array.from({ length: 5 }, () => [0, 1]) },
    '10F': { floorCount: 10, difficulties: Array.from({ length: 10 }, () => [1]) },
    '15F': {
      floorCount: 15,
      difficulties: Array.from({ length: 15 }, (_, floor) => (floor < 10 ? [1] : [3])),
    },
  },
  rules: {
    everyFloorPresent: ['publish'],
    themePackPresent: ['publish'],
    notRepeated: ['draft', 'publish'],
    sequence: ['draft', 'publish'],
    difficultyInRange: ['publish'],
    noNormalAfterHard: ['publish'],
    giftUnique: ['draft', 'publish'],
  },
}

type TableFile = typeof VALID_FILE

const withFiveFloors = (fiveFloors: object): object => ({
  ...VALID_FILE,
  categories: { ...VALID_FILE.categories, '5F': fiveFloors },
})

const withFirstFiveFloorSet = (difficulties: unknown[]): object =>
  withFiveFloors({
    floorCount: 5,
    difficulties: [difficulties, ...VALID_FILE.categories['5F'].difficulties.slice(1)],
  })

describe('FloorRuleTableSchema', () => {
  it('validates the table file', () => {
    expect(FloorRuleTableSchema.parse(VALID_FILE)).toEqual(VALID_FILE)
  })

  it('parses the same table whatever the key order', () => {
    const { rules, categories, stages, schemaVersion }: TableFile = VALID_FILE
    const reordered = { rules, categories, stages, schemaVersion }
    expect(FloorRuleTableSchema.parse(reordered)).toEqual(FloorRuleTableSchema.parse(VALID_FILE))
  })

  it.each([
    { name: 'an unknown root key', file: { ...VALID_FILE, version: 1 } },
    {
      name: 'an unknown category key',
      file: withFiveFloors({ ...VALID_FILE.categories['5F'], label: '5F' }),
    },
    {
      name: 'difficulties shorter than floorCount',
      file: withFiveFloors({ ...VALID_FILE.categories['5F'], floorCount: 4 }),
    },
    { name: 'floorCount 0', file: withFiveFloors({ floorCount: 0, difficulties: [] }) },
    { name: 'an empty difficulty set', file: withFirstFiveFloorSet([]) },
    { name: 'a difficulty above 3', file: withFirstFiveFloorSet([0, 4]) },
    { name: 'a difficulty below 0', file: withFirstFiveFloorSet([-1, 1]) },
    { name: 'a non-integer difficulty', file: withFirstFiveFloorSet([0.5]) },
    { name: 'a repeated difficulty', file: withFirstFiveFloorSet([1, 1]) },
    {
      name: 'a category the MD categories lack',
      file: {
        ...VALID_FILE,
        categories: { ...VALID_FILE.categories, '20F': { floorCount: 1, difficulties: [[1]] } },
      },
    },
    {
      name: 'a missing MD category',
      file: {
        ...VALID_FILE,
        categories: { '5F': VALID_FILE.categories['5F'], '10F': VALID_FILE.categories['10F'] },
      },
    },
    {
      name: 'a rule naming an unknown stage',
      file: { ...VALID_FILE, rules: { ...VALID_FILE.rules, giftUnique: ['draft', 'save'] } },
    },
    {
      name: 'a rule naming a stage the table does not list',
      file: {
        ...VALID_FILE,
        stages: ['draft', 'publish'],
        rules: { ...VALID_FILE.rules, sequence: ['index'] },
      },
    },
    {
      name: 'an unknown rule',
      file: { ...VALID_FILE, rules: { ...VALID_FILE.rules, order: ['draft'] } },
    },
    {
      name: 'a missing rule',
      file: {
        ...VALID_FILE,
        rules: Object.fromEntries(
          Object.entries(VALID_FILE.rules).filter(([rule]) => rule !== 'sequence'),
        ),
      },
    },
    {
      name: 'empty stages',
      file: {
        ...VALID_FILE,
        stages: [],
        rules: Object.fromEntries(Object.keys(VALID_FILE.rules).map((rule) => [rule, []])),
      },
    },
    {
      name: 'repeated stages',
      file: { ...VALID_FILE, stages: ['draft', 'draft', 'publish', 'index'] },
    },
    { name: 'a non-positive schemaVersion', file: { ...VALID_FILE, schemaVersion: 0 } },
  ])('rejects $name', ({ file }) => {
    expect(FloorRuleTableSchema.safeParse(file).success).toBe(false)
  })
})

describe('FLOOR_RULE_TABLE', () => {
  it('is the table file as parsed at module load', () => {
    expect(FLOOR_RULE_TABLE).toEqual(VALID_FILE)
  })

  it('answers floor counts, difficulty sets and rule stages from the table', () => {
    expect(floorCount('5F')).toBe(5)
    expect(floorCount('10F')).toBe(10)
    expect(floorCount('15F')).toBe(15)
    expect(allowedDifficulties('5F', 4)).toEqual([0, 1])
    expect(allowedDifficulties('15F', 9)).toEqual([1])
    expect(allowedDifficulties('15F', 10)).toEqual([3])
    expect(allowedDifficulties('10F', 10)).toBeUndefined()
    expect(stagesFor('notRepeated')).toEqual(['draft', 'publish'])
    expect(stagesFor('noNormalAfterHard')).toEqual(['publish'])
  })
})
