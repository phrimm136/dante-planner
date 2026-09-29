import { describe, it, expect } from 'vitest'

import { startGiftViolations } from '../startGift'
import { rendered } from '../../__tests__/rendered'

const violationsOf = (keyword: unknown, giftCount: number) =>
  rendered(startGiftViolations(keyword, giftCount))

describe('startGiftViolations', () => {
  it('passes no keyword with no gifts', () => {
    expect(violationsOf(null, 0)).toEqual([])
    expect(violationsOf(undefined, 0)).toEqual([])
  })

  it('passes a keyword with gifts', () => {
    expect(violationsOf('someKeyword', 2)).toEqual([])
  })

  it.each([
    ['null', null],
    ['absent', undefined],
    ['empty', ''],
  ])('reports gifts whose keyword is %s at the gift list', (_label, keyword) => {
    expect(violationsOf(keyword, 1)).toEqual([
      { code: 'MISSING_STARTGIFT_KEYWORD', path: 'selectedGiftIds' },
    ])
  })

  it('reports a keyword of the wrong kind as INVALID_FIELD_TYPE', () => {
    expect(violationsOf(5, 0)).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'selectedGiftKeyword' },
    ])
  })
})
