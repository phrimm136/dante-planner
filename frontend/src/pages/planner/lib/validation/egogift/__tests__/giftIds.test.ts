import { describe, it, expect } from 'vitest'

import { contentPath, field } from '../../contentPath'
import { giftIdViolations, parseGiftIds } from '../giftIds'
import type { DuplicateGiftCode, GiftReferenceData } from '../giftIds'
import { rendered } from '../../__tests__/rendered'

const GIFTS = contentPath(field('observationGiftIds'))

const knowsAll: GiftReferenceData = { hasEgoGift: () => true }
const knows = (ids: string[]): GiftReferenceData => ({ hasEgoGift: (id) => ids.includes(id) })

const violationsOf = (
  raw: unknown,
  data = knowsAll,
  duplicateCode: DuplicateGiftCode = 'DUPLICATE_EGOGIFT',
) => rendered(giftIdViolations(parseGiftIds(raw, GIFTS), duplicateCode, data))

describe('parseGiftIds', () => {
  it('keeps each string with its position and counts every element', () => {
    const parsed = parseGiftIds(['9001', 7, '9002'], GIFTS)

    expect(parsed.elementCount).toBe(3)
    expect(rendered(parsed.gifts)).toEqual([
      { giftId: '9001', path: 'observationGiftIds[0]' },
      { giftId: '9002', path: 'observationGiftIds[2]' },
    ])
    expect(rendered(parsed.violations)).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'observationGiftIds[1]' },
    ])
  })

  it('reads an absent list as empty', () => {
    expect(parseGiftIds(undefined, GIFTS)).toEqual({ elementCount: 0, gifts: [], violations: [] })
  })

  it.each([
    ['null', null],
    ['a string', '9001'],
    ['an object', {}],
  ])('reports a list that is %s as INVALID_FIELD_TYPE', (_label, raw) => {
    expect(rendered(parseGiftIds(raw, GIFTS).violations)).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'observationGiftIds' },
    ])
  })
})

describe('giftIdViolations', () => {
  it('passes unique ids', () => {
    expect(violationsOf(['9001', '9002', '9003'])).toEqual([])
  })

  it('reports each repeat with the given code at the repeat', () => {
    expect(violationsOf(['9001', '9002', '9001'])).toEqual([
      { code: 'DUPLICATE_EGOGIFT', path: 'observationGiftIds[2]' },
    ])
    expect(violationsOf(['9001', '9001'], knowsAll, 'DUPLICATE_STARTGIFT')).toEqual([
      { code: 'DUPLICATE_STARTGIFT', path: 'observationGiftIds[1]' },
    ])
  })

  it('reports an unknown id at its first occurrence and the repeat as a duplicate', () => {
    expect(violationsOf(['9999', '9999'], knows([]))).toEqual([
      { code: 'UNKNOWN_EGOGIFT', path: 'observationGiftIds[0]', giftId: '9999' },
      { code: 'DUPLICATE_EGOGIFT', path: 'observationGiftIds[1]' },
    ])
  })

  it('checks the ids the boundary kept and reports the rest as INVALID_FIELD_TYPE', () => {
    expect(violationsOf(['9001', null, '9999'], knows(['9001']))).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'observationGiftIds[1]' },
      { code: 'UNKNOWN_EGOGIFT', path: 'observationGiftIds[2]', giftId: '9999' },
    ])
  })
})
