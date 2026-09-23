import { describe, it, expect } from 'vitest'

import {
  formatSanityCondition,
  formatSanityConditions,
  parseSanityCondition,
  substituteArgs,
} from '../formatSanityCondition'

import type { SanityConditionI18n } from '@/shared/gameText'

const i18n: SanityConditionI18n = {
  OnKillEnemyAsLevelRatioMultiply: {
    inc: 'Increase by {0} on kill',
    dec: 'Decrease by {0} on kill',
  },
  OnWinDuelAsParryingCountMultiplyAndPlusPercent: {
    inc: 'Increase by {0} plus {1}%',
    dec: 'Decrease by {0} plus {1}%',
  },
  OnClash: { inc: 'Increase on clash', dec: 'Decrease on clash' },
}

describe('parseSanityCondition', () => {
  it.each([
    ['OnKillEnemyAsLevelRatioMultiply10', 'OnKillEnemyAsLevelRatioMultiply', [10]],
    [
      'OnWinDuelAsParryingCountMultiply10AndPlus20Percent',
      'OnWinDuelAsParryingCountMultiplyAndPlusPercent',
      [10, 20],
    ],
    ['OnClash', 'OnClash', []],
    ['Multiply007', 'Multiply', [7]],
  ])('%s parses to %s with %j', (encoded, baseName, args) => {
    expect(parseSanityCondition(encoded)).toEqual({ baseName, args })
  })
})

describe('substituteArgs', () => {
  it.each([
    ['plain text', [], 'plain text'],
    ['by {0}', [5], 'by 5'],
    ['by {0} then {1}', [5, 9], 'by 5 then 9'],
    ['repeats {0} and {0}', [3], 'repeats 3 and 3'],
    ['unfilled {1}', [3], 'unfilled {1}'],
  ])('%s with %j', (template, args, expected) => {
    expect(substituteArgs(template, args)).toBe(expected)
  })
})

describe('formatSanityCondition', () => {
  it.each([
    ['OnKillEnemyAsLevelRatioMultiply10', 'inc', 'Increase by 10 on kill'],
    ['OnKillEnemyAsLevelRatioMultiply10', 'dec', 'Decrease by 10 on kill'],
    ['OnWinDuelAsParryingCountMultiply10AndPlus20Percent', 'inc', 'Increase by 10 plus 20%'],
    ['OnClash', 'dec', 'Decrease on clash'],
  ] as const)('formats %s (%s)', (encoded, type, expected) => {
    expect(formatSanityCondition(encoded, i18n, type)).toEqual({ ok: true, value: expected })
  })

  it('reports the unresolved base name instead of logging', () => {
    expect(formatSanityCondition('OnUnknownThing42', i18n, 'inc')).toEqual({
      ok: false,
      error: { baseName: 'OnUnknownThing' },
    })
  })

  it('does not resolve prototype keys as translations', () => {
    expect(formatSanityCondition('toString', i18n, 'inc')).toEqual({
      ok: false,
      error: { baseName: 'toString' },
    })
  })
})

describe('formatSanityConditions', () => {
  it('returns one result per input, in order', () => {
    expect(
      formatSanityConditions(
        ['OnClash', 'OnUnknownThing', 'OnKillEnemyAsLevelRatioMultiply3'],
        i18n,
        'inc',
      ),
    ).toEqual([
      { ok: true, value: 'Increase on clash' },
      { ok: false, error: { baseName: 'OnUnknownThing' } },
      { ok: true, value: 'Increase by 3 on kill' },
    ])
  })

  it('returns an empty array for no inputs', () => {
    expect(formatSanityConditions([], i18n, 'inc')).toEqual([])
  })
})
