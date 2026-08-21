/**
 * giftToggle.test.ts
 *
 * The selection rules both gift selectors run on: toggling a gift at an
 * enhancement level, the recipe cascade that follows a first selection, and the
 * reachability gate a pack-restricted selector injects.
 */

import { describe, it, expect } from 'vitest'

import { buildEgoGiftSpecList } from '@/test-utils'
import { asEGOGiftId, asEncodedGiftId } from '@/test-utils/fixtures'
import { applyGiftToggle } from '../giftToggle'

const GIFT_9001 = asEGOGiftId('9001')
const GIFT_9002 = asEGOGiftId('9002')
const GIFT_9003 = asEGOGiftId('9003')
const GIFT_9004 = asEGOGiftId('9004')
const ENCODED_9001 = asEncodedGiftId('9001')
const ENCODED_9004 = asEncodedGiftId('9004')
const ENCODED_19001 = asEncodedGiftId('19001')
const ENCODED_29002 = asEncodedGiftId('29002')

const PACK = '1001'
const OTHER_PACK = '1002'

/** 9001 fuses from a general ingredient and one exclusive to another pack. */
const SPECS = buildEgoGiftSpecList({
  '9001': { themePack: [], recipe: { materials: [[GIFT_9002, GIFT_9003]] } },
  '9002': { themePack: [] },
  '9003': { themePack: [OTHER_PACK] },
  '9004': { themePack: [] },
})

const specById = new Map(Object.entries(SPECS))

/** The gate the floor selector supplies: reachable in this pack, or general. */
function reachableIn(packId: string) {
  return (ingredientId: string) => {
    const spec = specById.get(ingredientId)
    return !spec || spec.themePack.length === 0 || spec.themePack.includes(packId)
  }
}

describe('applyGiftToggle', () => {
  it('selects a gift at the requested enhancement', () => {
    expect(applyGiftToggle(new Set(), GIFT_9004, 1, { specById })).toEqual(new Set(['19004']))
  })

  it('selects the ingredients the recipe fuses alongside the gift', () => {
    expect(applyGiftToggle(new Set(), GIFT_9001, 0, { specById })).toEqual(
      new Set(['9001', '9002', '9003']),
    )
  })

  it('leaves the selection it was handed untouched', () => {
    const selected = new Set([ENCODED_9004])

    applyGiftToggle(selected, GIFT_9001, 0, { specById })

    expect(selected).toEqual(new Set(['9004']))
  })

  it('drops a gift toggled at the enhancement it already carries', () => {
    expect(applyGiftToggle(new Set([ENCODED_19001]), GIFT_9001, 1, { specById })).toEqual(new Set())
  })

  it('moves a selected gift to another enhancement without cascading again', () => {
    expect(applyGiftToggle(new Set([ENCODED_9001]), GIFT_9001, 2, { specById })).toEqual(
      new Set(['29001']),
    )
  })

  it('keeps an ingredient at the enhancement it was already selected at', () => {
    expect(applyGiftToggle(new Set([ENCODED_29002]), GIFT_9001, 0, { specById })).toEqual(
      new Set(['29002', '9001', '9003']),
    )
  })

  it('selects a gift the spec does not carry, cascading nothing', () => {
    expect(applyGiftToggle(new Set(), GIFT_9001, 0, { specById: new Map() })).toEqual(
      new Set(['9001']),
    )
  })

  it('skips an ingredient the gate rejects', () => {
    expect(
      applyGiftToggle(new Set(), GIFT_9001, 0, { specById, canCascade: reachableIn(PACK) }),
    ).toEqual(new Set(['9001', '9002']))
  })

  it('cascades every ingredient the gate accepts', () => {
    expect(
      applyGiftToggle(new Set(), GIFT_9001, 0, { specById, canCascade: reachableIn(OTHER_PACK) }),
    ).toEqual(new Set(['9001', '9002', '9003']))
  })
})
