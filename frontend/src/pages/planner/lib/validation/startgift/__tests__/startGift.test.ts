import { describe, it, expect } from 'vitest'

import { contentPath, field, index } from '../../contentPath'
import { startGiftViolations } from '../startGift'
import type { StartGiftReferenceData, StartGiftSeason, StartGifts } from '../startGift'
import { rendered } from '../../__tests__/rendered'

const KNOWS_ALL: StartGiftReferenceData = { hasEgoGift: () => true }

const EVERY_KEYWORD: StartGiftSeason = {
  hasStartGiftKeyword: () => true,
  inStartGiftPool: () => true,
}

const giftsOf = (giftIds: readonly string[]): StartGifts => ({
  elementCount: giftIds.length,
  gifts: giftIds.map((giftId, position) => ({
    giftId,
    path: contentPath(field('selectedGiftIds'), index(position)),
  })),
})

const placeholderGifts = (giftCount: number) =>
  giftsOf(Array.from({ length: giftCount }, (_, position) => String(9001 + position)))

const violationsOf = (keyword: unknown, giftCount: number) =>
  rendered(startGiftViolations(keyword, placeholderGifts(giftCount), KNOWS_ALL, EVERY_KEYWORD))

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

describe('start gift keyword and pool', () => {
  const POOLS: ReadonlyMap<string, ReadonlySet<string>> = new Map([
    ['Combustion', new Set(['9001', '9002'])],
    ['Slash', new Set(['9003'])],
  ])
  const season: StartGiftSeason = {
    hasStartGiftKeyword: (keyword) => POOLS.has(keyword),
    inStartGiftPool: (keyword, giftId) => POOLS.get(keyword)?.has(giftId) === true,
  }
  const known: StartGiftReferenceData = { hasEgoGift: (giftId) => giftId !== '9999' }

  const seasonal = (keyword: unknown, giftIds: string[], data = known) =>
    rendered(startGiftViolations(keyword, giftsOf(giftIds), data, season))

  it('passes gifts inside the keyword pool', () => {
    expect(seasonal('Combustion', ['9001', '9002'])).toEqual([])
  })

  it('reports a keyword outside the pools once, at the keyword', () => {
    expect(seasonal('Nope', ['9001'])).toEqual([
      { code: 'UNKNOWN_STARTGIFT_KEYWORD', path: 'selectedGiftKeyword' },
    ])
    expect(seasonal('Nope', [])).toEqual([
      { code: 'UNKNOWN_STARTGIFT_KEYWORD', path: 'selectedGiftKeyword' },
    ])
  })

  it('reports a known gift outside the pool at the gift', () => {
    expect(seasonal('Combustion', ['9001', '9003'])).toEqual([
      { code: 'MISMATCHED_STARTGIFT', path: 'selectedGiftIds[1]' },
    ])
  })

  it('compares an enhanced gift by its base id', () => {
    expect(seasonal('Combustion', ['19001', '29002'])).toEqual([])
  })

  it('skips the pool check for an unknown gift and a repeat', () => {
    expect(seasonal('Combustion', ['9999', '9003', '9003'])).toEqual([
      { code: 'MISMATCHED_STARTGIFT', path: 'selectedGiftIds[1]' },
    ])
  })

  it.each([
    ['blank', ''],
    ['whitespace', '  '],
  ])('reads a %s keyword as absent', (_label, keyword) => {
    expect(seasonal(keyword, ['9001'])).toEqual([
      { code: 'MISSING_STARTGIFT_KEYWORD', path: 'selectedGiftIds' },
    ])
    expect(seasonal(keyword, [])).toEqual([])
  })

  it('reports a keyword of the wrong kind with gifts only as INVALID_FIELD_TYPE', () => {
    expect(seasonal(5, ['9001'])).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'selectedGiftKeyword' },
    ])
  })

  it('checks only the kind of the keyword without a season', () => {
    const withoutSeason = (keyword: unknown, giftIds: string[]) =>
      rendered(startGiftViolations(keyword, giftsOf(giftIds), known, undefined))

    expect(withoutSeason('Nope', ['9003'])).toEqual([])
    expect(withoutSeason(null, ['9001'])).toEqual([])
    expect(withoutSeason(5, [])).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'selectedGiftKeyword' },
    ])
  })
})
