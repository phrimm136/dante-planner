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
  orderSelectionsByGiftOrder,
  lookupByGiftId,
  hasGiftId,
  giftDisplayName,
  getBaseGiftId,
  decodeGiftSelection,
} from '../egoGiftEncoding'
import { sortEGOGifts } from '../egoGiftSort'

import type { EGOGiftListItem, EGOGiftSpec } from '../../index'
import type { EnhancementLevel } from '@/shared/gameData'
import { toGiftListItem } from '../giftListItem'
import { asEncodedGiftId } from '@/test-utils/fixtures'

const ENCODED_9001 = asEncodedGiftId('9001')
const ENCODED_19001 = asEncodedGiftId('19001')
const ENCODED_29001 = asEncodedGiftId('29001')
const ENCODED_9002 = asEncodedGiftId('9002')
const ENCODED_19002 = asEncodedGiftId('19002')
const ENCODED_9003 = asEncodedGiftId('9003')
const ENCODED_9999 = asEncodedGiftId('9999')
const ENCODED_19999 = asEncodedGiftId('19999')

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
  ENCODED_9001, // base, in spec, translated
  ENCODED_19001, // enhancement 1, in spec
  ENCODED_29001, // enhancement 2, in spec
  ENCODED_9002, // in spec, untranslated
  ENCODED_19002, // enhanced, in spec, untranslated
  ENCODED_9003, // in spec, translated
  ENCODED_9999, // decodes, absent from spec
  ENCODED_19999, // decodes enhanced, absent from spec
]

/** Ids the callers can still be handed from storage, which no encoding accepts. */
const UNDECODABLE_IDS = ['abcd', '', '900', '390001']

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
        item: toGiftListItem(giftId, giftSpec, i18n[giftId] || giftId),
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

describe('decodeGiftSelections + orderSelectionsByGiftOrder', () => {
  it('matches the legacy component block over every id shape', () => {
    const legacy = legacyDecodeAndSort(IDS, SPEC, I18N)
    const next = orderSelectionsByGiftOrder(decodeGiftSelections(IDS, SPEC, I18N), 'tier-first')

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
    for (const id of UNDECODABLE_IDS) {
      expect(decodeGiftSelection(id)).toBeNull()
    }
  })

  it('falls back to the gift id when untranslated', () => {
    const byId = new Map(decodeGiftSelections(IDS, SPEC, I18N).map((s) => [s.encodedId, s.item]))
    expect(byId.get(ENCODED_9001)?.name).toBe('First Gift')
    expect(byId.get(ENCODED_9002)?.name).toBe('9002')
  })

  it('keeps each item paired with the enhancement it was selected at', () => {
    const sorted = orderSelectionsByGiftOrder(decodeGiftSelections(IDS, SPEC, I18N), 'tier-first')
    for (const { encodedId, item, enhancement } of sorted) {
      expect(getBaseGiftId(encodedId)).toBe(item.id)
      expect(enhancement).toBe(decodeGiftSelection(encodedId)?.enhancement)
    }
  })

  it('returns an empty list for an empty selection', () => {
    expect(orderSelectionsByGiftOrder(decodeGiftSelections([], SPEC, I18N), 'tier-first')).toEqual(
      [],
    )
  })
})

describe('lookupByGiftId / hasGiftId / giftDisplayName', () => {
  it.each(IDS)('lookupByGiftId matches the legacy decode-then-index for %s', (id) => {
    const baseId = getBaseGiftId(id)
    const legacy = SPEC[baseId]
    expect(lookupByGiftId(id, SPEC)).toBe(legacy)
  })

  it.each(IDS)('hasGiftId matches the legacy `in` guard for %s', (id) => {
    const baseId = getBaseGiftId(id)
    expect(hasGiftId(id, SPEC)).toBe(baseId in SPEC)
  })

  it.each(IDS)('giftDisplayName matches the legacy i18n fallback for %s', (id) => {
    const baseId = getBaseGiftId(id)
    const legacy = I18N[baseId] ?? id
    expect(giftDisplayName(id, I18N)).toBe(legacy)
  })

  it('treats an absent i18n catalogue as untranslated', () => {
    expect(giftDisplayName(ENCODED_9001, {})).toBe('9001')
  })
})
