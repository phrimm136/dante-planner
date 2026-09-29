import { describe, it, expect } from 'vitest'

import { startGiftViolations } from '../startGift'
import type { StartGiftReferenceData, StartGiftSeason } from '../startGift'
import { rendered } from '../../__tests__/rendered'

const KNOWS_ALL: StartGiftReferenceData = { hasEgoGift: () => true }

const EVERY_KEYWORD: StartGiftSeason = {
  hasStartGiftKeyword: () => true,
  inStartGiftPool: () => true,
}

const placeholderGifts = (giftCount: number) =>
  Array.from({ length: giftCount }, (_, position) => String(9001 + position))

const violationsOf = (keyword: string | null, giftCount: number) =>
  rendered(startGiftViolations(keyword, placeholderGifts(giftCount), KNOWS_ALL, EVERY_KEYWORD))

describe('startGiftViolations', () => {
  it('passes no keyword with no gifts', () => {
    expect(violationsOf(null, 0)).toEqual([])
  })

  it('passes a keyword with gifts', () => {
    expect(violationsOf('someKeyword', 2)).toEqual([])
  })

  it('reports gifts without a keyword at the gift list', () => {
    expect(violationsOf(null, 1)).toEqual([
      { code: 'MISSING_STARTGIFT_KEYWORD', path: 'selectedGiftIds' },
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

  const seasonal = (keyword: string | null, giftIds: string[], data = known) =>
    rendered(startGiftViolations(keyword, giftIds, data, season))

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
})
