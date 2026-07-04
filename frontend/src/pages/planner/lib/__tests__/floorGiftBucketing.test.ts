/**
 * floorGiftBucketing.test.ts
 *
 * Pins the floor selector's bucketing semantics:
 *   - "Themed to this pack" = gift.themePack non-empty AND includes pack id
 *   - "General" = gift.themePack empty (acquirable in any pack via random fusion)
 *   - "Hidden" = themed to other packs only (genuinely unobtainable in this floor)
 *
 * These cases exist to catch the historical regression where the selector
 * filtered by "any-pool membership" (gift.themePack as union of egoGiftPool +
 * specificEgoGiftPool), which made nearly every general fusion appear themed
 * to dozens of packs and stopped distinguishing pack-distinctive content.
 *
 * Anchored test fixtures match real game data:
 *   9410  themePack=[1004, 1005] — co-themed fusion (9408+9409 across two packs)
 *   9272  themePack=[1127]       — themed fusion (one ingredient 1127-exclusive)
 *   9092  themePack=[]           — general fusion (all general ingredients)
 *   9408  themePack=[1004]       — single-pack exclusive base
 */

import { describe, it, expect } from 'vitest'
import { bucketAndSortFloorGifts } from '../floorGiftBucketing'
import { DUNGEON_IDX } from '@/shared/gameData'
import type { EGOGiftListItem } from '@/pages/egoGift'

// EGOGiftListItem resolves to an error/any type under oxlint's type-aware pass because test
// files are excluded from tsconfig; the intersection is well-typed under tsc. False positive.
// oxlint-disable-next-line typescript/no-redundant-type-constituents
function makeGift(overrides: Partial<EGOGiftListItem> & { id: string }): EGOGiftListItem {
  return {
    tag: ['TIER_2'],
    keyword: null,
    battleKeywordList: [],
    attributeType: 'CRIMSON',
    themePack: [],
    maxEnhancement: 0,
    ...overrides,
  }
}

const G_9092 = makeGift({ id: '9092', themePack: [], tag: ['TIER_4'] })
const G_9272 = makeGift({ id: '9272', themePack: ['1127'], tag: ['TIER_4'] })
const G_9410 = makeGift({ id: '9410', themePack: ['1004', '1005'], tag: ['TIER_4'] })
const G_9408 = makeGift({ id: '9408', themePack: ['1004'], tag: ['TIER_2'] })
const G_9409 = makeGift({ id: '9409', themePack: ['1005'], tag: ['TIER_2'] })
const G_9271 = makeGift({ id: '9271', themePack: ['1127'], tag: ['TIER_2'] })
const G_9788 = makeGift({ id: '9788', themePack: ['1117', '1127'], tag: ['TIER_2'] })

// 9242 = fixedReward-only exclusive (only appears in 3001.fixedRewardEgoGifts;
// nowhere else). Pipeline promotes single-pack-appearance gifts to that pack's
// specificEgoGiftPool so the FE just sees themePack=['3001'] like any other
// themed gift. The neighboring fixedReward entries (9083/9082/9081) appear in
// many packs' egoGiftPool, so they are NOT promoted and stay general.
const G_9242 = makeGift({ id: '9242', themePack: ['3001'], tag: ['TIER_4'] })
const G_9083 = makeGift({ id: '9083', themePack: [], tag: ['TIER_3'] })

describe('bucketAndSortFloorGifts', () => {
  describe('regression: 9410 must appear in both 1004 and 1005 (co-themed fusion)', () => {
    it('shows 9410 in pack 1004', () => {
      const out = bucketAndSortFloorGifts([G_9410], '1004', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).toContain('9410')
    })

    it('shows 9410 in pack 1005', () => {
      const out = bucketAndSortFloorGifts([G_9410], '1005', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).toContain('9410')
    })

    it('hides 9410 in unrelated pack 1001', () => {
      const out = bucketAndSortFloorGifts([G_9410], '1001', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).not.toContain('9410')
    })
  })

  describe('regression: 9272 must appear only in 1127', () => {
    it('shows 9272 in pack 1127', () => {
      const out = bucketAndSortFloorGifts([G_9272], '1127', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).toContain('9272')
    })

    it('hides 9272 in pack 1117 even though some ingredients are co-themed there', () => {
      const out = bucketAndSortFloorGifts([G_9272], '1117', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).not.toContain('9272')
    })

    it('hides 9272 in unrelated pack 1001', () => {
      const out = bucketAndSortFloorGifts([G_9272], '1001', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).not.toContain('9272')
    })
  })

  describe('regression: 9092 (general fusion) must appear in every pack', () => {
    it.each(['1001', '1004', '1117', '1127', '3001'])('shows 9092 in pack %s', (packId) => {
      const out = bucketAndSortFloorGifts([G_9092], packId, DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).toContain('9092')
    })
  })

  describe('regression: single-pack exclusives are hidden in other packs', () => {
    it('shows 9408 in 1004', () => {
      const out = bucketAndSortFloorGifts([G_9408], '1004', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).toContain('9408')
    })

    it('hides 9408 in 1005', () => {
      const out = bucketAndSortFloorGifts([G_9408], '1005', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).not.toContain('9408')
    })
  })

  describe('regression: co-exclusive gifts appear in each owner pack', () => {
    it('shows 9788 (themePack=[1117,1127]) in 1117', () => {
      const out = bucketAndSortFloorGifts([G_9788], '1117', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).toContain('9788')
    })

    it('shows 9788 in 1127', () => {
      const out = bucketAndSortFloorGifts([G_9788], '1127', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).toContain('9788')
    })

    it('hides 9788 in unrelated pack 1001', () => {
      const out = bucketAndSortFloorGifts([G_9788], '1001', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).not.toContain('9788')
    })
  })

  describe('bucket order: themed-to-this precedes general', () => {
    it('places themed gifts before general gifts regardless of input order', () => {
      const input = [G_9092, G_9408]  // general first, themed second
      const out = bucketAndSortFloorGifts(input, '1004', DUNGEON_IDX.NORMAL, 'tier-first')
      const ids = out.map(g => g.id)
      expect(ids.indexOf('9408')).toBeLessThan(ids.indexOf('9092'))
    })

    it('themed bucket and general bucket sort independently', () => {
      // Two themed (different tiers) + two general (different tiers)
      const themedHigh = makeGift({ id: '9999', themePack: ['1004'], tag: ['TIER_4'] })
      const themedLow = makeGift({ id: '9998', themePack: ['1004'], tag: ['TIER_1'] })
      const generalHigh = makeGift({ id: '8999', themePack: [], tag: ['TIER_4'] })
      const generalLow = makeGift({ id: '8998', themePack: [], tag: ['TIER_1'] })

      const out = bucketAndSortFloorGifts(
        [generalLow, themedLow, generalHigh, themedHigh],
        '1004', DUNGEON_IDX.NORMAL, 'tier-first'
      )
      const ids = out.map(g => g.id)
      // Both themed before any general
      expect(Math.max(ids.indexOf('9998'), ids.indexOf('9999')))
        .toBeLessThan(Math.min(ids.indexOf('8998'), ids.indexOf('8999')))
    })
  })

  describe('difficulty filter', () => {
    const hardGift = makeGift({ id: '9100', hardOnly: true })
    const extremeGift = makeGift({ id: '9101', extremeOnly: true })
    const normalGift = makeGift({ id: '9102' })

    it('hides hardOnly in NORMAL', () => {
      const out = bucketAndSortFloorGifts([hardGift, normalGift], '1001', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).toEqual(['9102'])
    })

    it('shows hardOnly in HARD', () => {
      const out = bucketAndSortFloorGifts([hardGift, normalGift], '1001', DUNGEON_IDX.HARD, 'tier-first')
      expect(out.map(g => g.id).sort((a, b) => a.localeCompare(b))).toEqual(['9100', '9102'])
    })

    it('hides extremeOnly in HARD', () => {
      const out = bucketAndSortFloorGifts([extremeGift, normalGift], '1001', DUNGEON_IDX.HARD, 'tier-first')
      expect(out.map(g => g.id)).toEqual(['9102'])
    })

    it('shows extremeOnly in EXTREME', () => {
      const out = bucketAndSortFloorGifts([extremeGift, normalGift], '1001', DUNGEON_IDX.EXTREME, 'tier-first')
      expect(out.map(g => g.id).sort((a, b) => a.localeCompare(b))).toEqual(['9101', '9102'])
    })
  })

  describe('regression: fixedReward-only exclusivity (9242)', () => {
    // The pipeline promotes single-pack-appearance gifts (incl. fixedReward-
    // only) to that pack's specificEgoGiftPool. From the FE's perspective
    // 9242.themePack=['3001'], so it must surface in 3001 and hide elsewhere.
    // The neighboring general gifts in the same fixedReward list (9083 etc.)
    // must NOT inherit theming.
    it('shows 9242 in pack 3001', () => {
      const out = bucketAndSortFloorGifts([G_9242], '3001', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).toContain('9242')
    })

    it('hides 9242 in pack 1001', () => {
      const out = bucketAndSortFloorGifts([G_9242], '1001', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out.map(g => g.id)).not.toContain('9242')
    })

    it('shows 9083 (general fixedReward sibling) in every pack including 3001', () => {
      for (const packId of ['1001', '3001', '1127']) {
        const out = bucketAndSortFloorGifts([G_9083], packId, DUNGEON_IDX.NORMAL, 'tier-first')
        expect(out.map(g => g.id)).toContain('9083')
      }
    })
  })

  describe('regression guard: filter must NOT pass everything', () => {
    // The original regression was that the filter let through ~all gifts because
    // it conflated egoGiftPool memberships with theming. Lock in that other-pack
    // exclusives are filtered out.
    it('hides every non-1004 single-pack exclusive when planning for 1004', () => {
      const otherPackExclusives = [G_9409, G_9271]  // themePack=[1005], [1127]
      const out = bucketAndSortFloorGifts(otherPackExclusives, '1004', DUNGEON_IDX.NORMAL, 'tier-first')
      expect(out).toEqual([])
    })

    it('integrated case: 9272 hidden, 9410 visible, 9092 visible in 1004', () => {
      const out = bucketAndSortFloorGifts(
        [G_9092, G_9272, G_9410, G_9408, G_9409, G_9271],
        '1004', DUNGEON_IDX.NORMAL, 'tier-first'
      )
      const ids = new Set(out.map(g => g.id))
      expect(ids.has('9410')).toBe(true)   // co-themed includes 1004
      expect(ids.has('9408')).toBe(true)   // 1004-exclusive
      expect(ids.has('9092')).toBe(true)   // general
      expect(ids.has('9272')).toBe(false)  // 1127-exclusive
      expect(ids.has('9409')).toBe(false)  // 1005-exclusive
      expect(ids.has('9271')).toBe(false)  // 1127-exclusive
    })
  })
})
