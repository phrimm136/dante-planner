/**
 * The decode-then-resolve block these helpers replaced was duplicated verbatim
 * across the floor viewer and the comprehensive summary, and the
 * decode-then-index-a-map guard across the planner rules and validator. The
 * originals are transcribed here and asserted equal over ids that exercise every
 * branch: valid, enhanced, unknown to the spec, and unencodable.
 */

import { describe, it, expect } from 'vitest'

import {
  decodeGiftSelections,
  sortGiftSelections,
  lookupByGiftId,
  hasGiftId,
  giftDisplayName,
  getBaseGiftId,
  decodeGiftSelection,
} from '../egoGiftEncoding'
import { sortEGOGifts } from '../egoGiftSort'

import type { EGOGiftListItem, EGOGiftSpec } from '../../index'
import type { EnhancementLevel } from '@/shared/gameData'

const SPEC: Record<string, EGOGiftSpec> = {
  '9001': {
    tag: ['TIER_1'],
    keyword: 'Burst',
    battleKeywordList: ['Poise'],
    attributeType: 'CRIMSON',
    themePack: ['1'],
    maxEnhancement: 2,
  } as unknown as EGOGiftSpec,
  '9002': {
    tag: ['TIER_3'],
    keyword: null,
    attributeType: 'AZURE',
    maxEnhancement: 1,
  } as unknown as EGOGiftSpec,
  '9003': {
    tag: ['TIER_2'],
    keyword: 'Combustion',
    battleKeywordList: [],
    attributeType: 'SCARLET',
    themePack: [],
    maxEnhancement: 0,
  } as unknown as EGOGiftSpec,
}

const I18N: Record<string, string> = { '9001': 'First Gift', '9003': 'Third Gift' }

/** Every id shape the callers can hold, including ones that must be dropped. */
const IDS = [
  '9001', // base, in spec, translated
  '19001', // enhancement 1, in spec
  '29001', // enhancement 2, in spec
  '9002', // in spec, untranslated
  '19002', // enhanced, in spec, untranslated
  '9003', // in spec, translated
  '9999', // decodes, absent from spec
  '19999', // decodes enhanced, absent from spec
  'abcd', // does not decode
  '', // does not decode
  '900', // too short
  '390001', // enhancement digit out of range
]

interface LegacyDecoded {
  item: EGOGiftListItem
  enhancement: EnhancementLevel
}

/** Verbatim transcription of the duplicated component block. */
function legacyDecodeAndSort(
  selectedGiftIds: Iterable<string>,
  spec: Record<string, EGOGiftSpec>,
  i18n: Record<string, string>,
) {
  const gifts: LegacyDecoded[] = []
  for (const encodedId of selectedGiftIds) {
    const decoded = decodeGiftSelection(encodedId)
    if (!decoded) continue
    const { giftId, enhancement } = decoded
    const giftSpec = spec[giftId]
    if (giftSpec) {
      gifts.push({
        item: {
          id: giftId,
          name: i18n[giftId] || giftId,
          tag: giftSpec.tag as EGOGiftListItem['tag'],
          keyword: giftSpec.keyword,
          battleKeywordList: giftSpec.battleKeywordList ?? [],
          attributeType: giftSpec.attributeType,
          themePack: giftSpec.themePack,
          maxEnhancement: giftSpec.maxEnhancement,
        },
        enhancement,
      })
    }
  }
  const enhancementMap = new Map(gifts.map((g) => [g.item.id, g.enhancement]))
  return sortEGOGifts(
    gifts.map((g) => g.item),
    'tier-first',
  ).map((item) => ({ item, enhancement: enhancementMap.get(item.id)! }))
}

describe('decodeGiftSelections + sortGiftSelections', () => {
  it('matches the legacy component block over every id shape', () => {
    const legacy = legacyDecodeAndSort(IDS, SPEC, I18N)
    const next = sortGiftSelections(decodeGiftSelections(IDS, SPEC, I18N), 'tier-first')

    expect(next.map(({ item, enhancement }) => ({ item, enhancement }))).toEqual(legacy)
  })

  it('drops ids that do not decode and ids the spec does not carry', () => {
    expect(decodeGiftSelections(IDS, SPEC, I18N).map((s) => s.encodedId)).toEqual([
      '9001',
      '19001',
      '29001',
      '9002',
      '19002',
      '9003',
    ])
  })

  it('falls back to the gift id when untranslated', () => {
    const byId = new Map(decodeGiftSelections(IDS, SPEC, I18N).map((s) => [s.encodedId, s.item]))
    expect(byId.get('9001')?.name).toBe('First Gift')
    expect(byId.get('9002')?.name).toBe('9002')
  })

  it('keeps each item paired with the enhancement it was selected at', () => {
    const sorted = sortGiftSelections(decodeGiftSelections(IDS, SPEC, I18N), 'tier-first')
    for (const { encodedId, item, enhancement } of sorted) {
      expect(getBaseGiftId(encodedId)).toBe(item.id)
      expect(enhancement).toBe(decodeGiftSelection(encodedId)?.enhancement)
    }
  })

  it('returns an empty list for an empty selection', () => {
    expect(sortGiftSelections(decodeGiftSelections([], SPEC, I18N), 'tier-first')).toEqual([])
  })
})

describe('lookupByGiftId / hasGiftId / giftDisplayName', () => {
  it.each(IDS)('lookupByGiftId matches the legacy decode-then-index for %s', (id) => {
    const baseId = getBaseGiftId(id)
    const legacy = baseId === null ? undefined : SPEC[baseId]
    expect(lookupByGiftId(id, SPEC)).toBe(legacy)
  })

  it.each(IDS)('hasGiftId matches the legacy `in` guard for %s', (id) => {
    const baseId = getBaseGiftId(id)
    expect(hasGiftId(id, SPEC)).toBe(!(baseId === null || !(baseId in SPEC)))
  })

  it.each(IDS)('giftDisplayName matches the legacy i18n fallback for %s', (id) => {
    const baseId = getBaseGiftId(id)
    const legacy = (baseId === null ? undefined : I18N[baseId]) ?? id
    expect(giftDisplayName(id, I18N)).toBe(legacy)
  })

  it('treats an absent i18n catalogue as untranslated', () => {
    expect(giftDisplayName('9001', {})).toBe('9001')
  })
})
