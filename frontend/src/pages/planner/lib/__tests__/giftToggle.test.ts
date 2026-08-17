/**
 * giftToggle.test.ts
 *
 * The selection rules both gift selectors run on: toggling a gift at an
 * enhancement level, the recipe cascade that follows a first selection, and the
 * reachability gate a pack-restricted selector injects.
 */

import { describe, it, expect } from 'vitest'

import { EGOGiftIdSchema } from '@/shared/gameData'
import { buildEgoGiftSpecList } from '@/test-utils'
import { applyGiftToggle } from '../giftToggle'

const giftId = (id: string) => EGOGiftIdSchema.parse(id)

const PACK = '1001'
const OTHER_PACK = '1002'

/** 9001 fuses from a general ingredient and one exclusive to another pack. */
const SPECS = buildEgoGiftSpecList({
  '9001': { themePack: [], recipe: { materials: [[giftId('9002'), giftId('9003')]] } },
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
    expect(applyGiftToggle(new Set(), '9004', 1, { specById })).toEqual(new Set(['19004']))
  })

  it('selects the ingredients the recipe fuses alongside the gift', () => {
    expect(applyGiftToggle(new Set(), '9001', 0, { specById })).toEqual(
      new Set(['9001', '9002', '9003']),
    )
  })

  it('leaves the selection it was handed untouched', () => {
    const selected = new Set(['9004'])

    applyGiftToggle(selected, '9001', 0, { specById })

    expect(selected).toEqual(new Set(['9004']))
  })

  it('drops a gift toggled at the enhancement it already carries', () => {
    expect(applyGiftToggle(new Set(['19001']), '9001', 1, { specById })).toEqual(new Set())
  })

  it('moves a selected gift to another enhancement without cascading again', () => {
    expect(applyGiftToggle(new Set(['9001']), '9001', 2, { specById })).toEqual(new Set(['29001']))
  })

  it('keeps an ingredient at the enhancement it was already selected at', () => {
    expect(applyGiftToggle(new Set(['29002']), '9001', 0, { specById })).toEqual(
      new Set(['29002', '9001', '9003']),
    )
  })

  it('selects a gift the spec does not carry, cascading nothing', () => {
    expect(applyGiftToggle(new Set(), '9001', 0, { specById: new Map() })).toEqual(
      new Set(['9001']),
    )
  })

  it('skips an ingredient the gate rejects', () => {
    expect(
      applyGiftToggle(new Set(), '9001', 0, { specById, canCascade: reachableIn(PACK) }),
    ).toEqual(new Set(['9001', '9002']))
  })

  it('cascades every ingredient the gate accepts', () => {
    expect(
      applyGiftToggle(new Set(), '9001', 0, { specById, canCascade: reachableIn(OTHER_PACK) }),
    ).toEqual(new Set(['9001', '9002', '9003']))
  })
})
