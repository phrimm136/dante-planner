/**
 * The key-ladder table replaced a comparator that recomputed all six keys and
 * branched on the sort mode inline. The original is transcribed here and
 * asserted to produce the same order over a fixture set built to force ties at
 * every level of the ladder.
 */

import { describe, it, expect } from 'vitest'

import { sortEGOGifts } from '../egoGiftSort'
import { KEYWORD_ORDER } from '@/shared/gameData'

import type { EGOGiftListItem } from '../../types/EGOGiftTypes'
import type { SortMode } from '@/shared/filter'
import { EGOGiftIdSchema } from '@/shared/gameData'

function getCategoryIndex(keyword: string | null): number {
  if (!keyword) return KEYWORD_ORDER.indexOf('None')
  const index = KEYWORD_ORDER.indexOf(keyword as (typeof KEYWORD_ORDER)[number])
  return index !== -1 ? index : KEYWORD_ORDER.indexOf('None')
}

function extractTier(tag: string[]): string | null {
  const exTier = tag.find((t) => t === 'TIER_EX')
  if (exTier) return 'EX'
  return tag.find((t) => t.startsWith('TIER_'))?.replace('TIER_', '') || null
}

function getTierValue(tag: string[]): number {
  const tier = extractTier(tag)
  if (!tier) return 999
  if (tier === 'EX') return 0
  const tierNum = parseInt(tier, 10)
  return isNaN(tierNum) ? 999 : 6 - tierNum
}

/** Verbatim transcription of the pre-table comparator. */
function legacySort(gifts: EGOGiftListItem[], sortMode: SortMode): EGOGiftListItem[] {
  return [...gifts].sort((a, b) => {
    const aCategoryIndex = getCategoryIndex(a.keyword)
    const bCategoryIndex = getCategoryIndex(b.keyword)
    const aTierValue = getTierValue(a.tag)
    const bTierValue = getTierValue(b.tag)
    const aId = parseInt(a.id, 10)
    const bId = parseInt(b.id, 10)

    if (sortMode === 'tier-first') {
      if (aTierValue !== bTierValue) return aTierValue - bTierValue
      if (aCategoryIndex !== bCategoryIndex) return aCategoryIndex - bCategoryIndex
      return aId - bId
    } else {
      if (aCategoryIndex !== bCategoryIndex) return aCategoryIndex - bCategoryIndex
      if (aTierValue !== bTierValue) return aTierValue - bTierValue
      return aId - bId
    }
  })
}

const KEYWORDS: Array<string | null> = [
  KEYWORD_ORDER[0],
  KEYWORD_ORDER[1],
  'None',
  'NotAKeyword',
  null,
]
const TAGS: string[][] = [
  ['TIER_EX'],
  ['TIER_5'],
  ['TIER_1'],
  ['TIER_3', 'TIER_EX'],
  ['TIER_X'],
  ['NOT_A_TIER'],
  [],
]

/** Every keyword × tag pair, each at two ids, so ties break at each ladder level. */
const GIFTS: EGOGiftListItem[] = KEYWORDS.flatMap((keyword, k) =>
  TAGS.flatMap((tag, t) =>
    [0, 1].map((n) => ({
      id: EGOGiftIdSchema.parse(String(9000 + k * 20 + t * 2 + n)),
      name: `Gift ${k}-${t}-${n}`,
      tag: tag as EGOGiftListItem['tag'],
      keyword,
      battleKeywordList: [],
      attributeType: 'CRIMSON',
      themePack: [],
      maxEnhancement: 2,
    })),
  ),
)

const MODES: SortMode[] = ['tier-first', 'keyword-first']

describe('sortEGOGifts key ladder', () => {
  it.each(MODES)('matches the legacy comparator for %s', (mode) => {
    expect(sortEGOGifts(GIFTS, mode).map((g) => g.id)).toEqual(
      legacySort(GIFTS, mode).map((g) => g.id),
    )
  })

  it.each(MODES)('matches the legacy comparator on a reversed input for %s', (mode) => {
    const reversed = [...GIFTS].reverse()
    expect(sortEGOGifts(reversed, mode).map((g) => g.id)).toEqual(
      legacySort(reversed, mode).map((g) => g.id),
    )
  })

  it('does not mutate its input', () => {
    const before = GIFTS.map((g) => g.id)
    sortEGOGifts(GIFTS, 'tier-first')
    expect(GIFTS.map((g) => g.id)).toEqual(before)
  })

  it('orders the two modes differently', () => {
    expect(sortEGOGifts(GIFTS, 'tier-first').map((g) => g.id)).not.toEqual(
      sortEGOGifts(GIFTS, 'keyword-first').map((g) => g.id),
    )
  })

  it('returns an empty array unchanged', () => {
    expect(sortEGOGifts([], 'tier-first')).toEqual([])
  })
})
