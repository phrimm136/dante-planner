/**
 * abEventFacets.parity.test.ts
 *
 * Pins AB_EVENT_FACETS to the per-facet predicates AbEventList called before
 * the facet migration, over the full cartesian product of selections.
 */

import { describe, it, expect } from 'vitest'
import { applyFacets } from '@/shared/filter'
import { enumerateSelectionStates, findParityMismatches } from '@/test-utils/facetParity'
import { AB_EVENT_FACETS, type AbEventFacetState } from '../abEventFilter'
import type { AbEventSpecListEntry } from '../../schemas/AbEventSchemas'

function legacyRelatedEgoGift(
  entry: AbEventSpecListEntry,
  selectedEgoGifts: ReadonlySet<string>,
): boolean {
  if (selectedEgoGifts.size === 0) return true
  return entry.relatedEgoGifts.some((giftId) => selectedEgoGifts.has(giftId))
}

function legacyRelatedThemePack(
  entry: AbEventSpecListEntry,
  selectedThemePacks: ReadonlySet<string>,
): boolean {
  if (selectedThemePacks.size === 0) return true
  return entry.relatedThemePacks.some((packId) => selectedThemePacks.has(packId))
}

interface EventFixture {
  id: string
  entry: AbEventSpecListEntry
}

function legacyMatches(event: EventFixture, state: AbEventFacetState): boolean {
  if (!legacyRelatedEgoGift(event.entry, state.selectedEgoGifts)) return false
  if (!legacyRelatedThemePack(event.entry, state.selectedThemePacks)) return false
  return true
}

function makeEvent(id: string, overrides: Partial<AbEventSpecListEntry> = {}): EventFixture {
  return {
    id,
    entry: {
      relatedEgoGifts: [],
      relatedThemePacks: [],
      hasImage: true,
      ...overrides,
    },
  }
}

const ITEMS: EventFixture[] = [
  makeEvent('101', { relatedEgoGifts: ['9001', '991002'], relatedThemePacks: ['1002', '1003'] }),
  makeEvent('102', { relatedEgoGifts: ['9001'], relatedThemePacks: ['1002'] }),
  makeEvent('103', { relatedEgoGifts: [], relatedThemePacks: ['1003'] }),
  makeEvent('104', { relatedEgoGifts: ['991002'], relatedThemePacks: [] }),
  makeEvent('105'),
]

const BASE_STATE: AbEventFacetState = {
  selectedEgoGifts: new Set(),
  selectedThemePacks: new Set(),
}

const STATES = enumerateSelectionStates(BASE_STATE, {
  selectedEgoGifts: [[], ['9001'], ['991002'], ['9001', '991002'], ['9999']],
  selectedThemePacks: [[], ['1002'], ['1003'], ['1002', '1003'], ['9999']],
})

describe('AB_EVENT_FACETS parity', () => {
  it('enumerates every selection combination', () => {
    expect(STATES.length).toBe(5 ** 2)
  })

  it('survives the same ids as the pre-migration predicates', () => {
    const mismatches = findParityMismatches(
      ITEMS,
      STATES,
      (item, state) => legacyMatches(item, state),
      (item, state) => applyFacets(item.entry, state, AB_EVENT_FACETS),
    )
    expect(mismatches).toEqual([])
  })

  it('keeps both facets on ANY', () => {
    const bothGifts: AbEventFacetState = {
      ...BASE_STATE,
      selectedEgoGifts: new Set(['9001', '991002']),
    }
    const survivors = ITEMS.filter((item) => applyFacets(item.entry, bothGifts, AB_EVENT_FACETS))
    expect(survivors.map((item) => item.id)).toEqual(['101', '102', '104'])
  })
})
