/**
 * egoGiftFacets.parity.test.ts
 *
 * Pins EGO_GIFT_FACETS to the per-facet predicates EGOGiftList called before
 * the facet migration, over a capped cartesian product of selections.
 */

import { describe, it, expect } from 'vitest'
import { applyFacets } from '@/shared/filter'
import type { EGOGiftAttributeType, EGOGiftDifficulty, EGOGiftTier } from '@/shared/gameData'
import { enumerateSelectionStates, findParityMismatches } from '@/test-utils/facetParity'
import { EGO_GIFT_FACETS, deriveDifficulty, type EGOGiftFacetState } from '../egoGiftFilter'
import { parseTier, toRomanTier } from '../egoGiftTier'
import type { EGOGiftListItem } from '../../types/EGOGiftTypes'
import { EGOGiftIdSchema } from '@/shared/gameData'

function legacyKeyword(giftKeyword: string | null, selectedKeywords: ReadonlySet<string>): boolean {
  if (selectedKeywords.size === 0) return true
  if (giftKeyword === null) return selectedKeywords.has('None')
  return selectedKeywords.has(giftKeyword)
}

function legacyDifficulty(
  gift: { hardOnly?: boolean; extremeOnly?: boolean },
  selectedDifficulties: ReadonlySet<EGOGiftDifficulty>,
): boolean {
  if (selectedDifficulties.size === 0) return true
  return selectedDifficulties.has(deriveDifficulty(gift))
}

function legacyTier(tag: readonly string[], selectedTiers: ReadonlySet<EGOGiftTier>): boolean {
  if (selectedTiers.size === 0) return true
  const tier = toRomanTier(parseTier(tag))
  return tier !== undefined && selectedTiers.has(tier)
}

function legacyThemePack(
  themePacks: readonly (string | number)[] | undefined,
  selectedThemePacks: ReadonlySet<string>,
): boolean {
  if (selectedThemePacks.size === 0) return true
  const giftThemePacks = themePacks ?? []
  return giftThemePacks.some((tp) => selectedThemePacks.has(String(tp)))
}

function legacyAttributeType(
  attributeType: string | undefined,
  selectedAttributeTypes: ReadonlySet<string>,
): boolean {
  if (selectedAttributeTypes.size === 0) return true
  return attributeType !== undefined && selectedAttributeTypes.has(attributeType)
}

function legacyFusioned(
  fusioned: boolean | undefined,
  selectedFusioned: ReadonlySet<string>,
): boolean {
  if (selectedFusioned.size === 0) return true
  const isFusioned = fusioned === true
  if (selectedFusioned.has('Y') && isFusioned) return true
  if (selectedFusioned.has('N') && !isFusioned) return true
  return false
}

function legacyExclusive(
  themePacks: readonly string[] | undefined,
  selectedExclusive: ReadonlySet<string>,
): boolean {
  if (selectedExclusive.size === 0) return true
  const isExclusive = (themePacks ?? []).length > 0
  if (selectedExclusive.has('Y') && isExclusive) return true
  if (selectedExclusive.has('N') && !isExclusive) return true
  return false
}

function legacyMatches(gift: EGOGiftListItem, state: EGOGiftFacetState): boolean {
  if (!legacyKeyword(gift.keyword, state.selectedKeywords)) return false

  if (state.selectedBattleKeywords.size > 0) {
    const hasAnyBattleKeyword = (gift.battleKeywordList ?? []).some((keyword) =>
      state.selectedBattleKeywords.has(keyword),
    )
    if (!hasAnyBattleKeyword) return false
  }

  if (!legacyDifficulty(gift, state.selectedDifficulties)) return false
  if (!legacyTier(gift.tag, state.selectedTiers)) return false
  if (!legacyThemePack(gift.themePack, state.selectedThemePacks)) return false
  if (!legacyAttributeType(gift.attributeType, state.selectedAttributeTypes)) return false
  if (!legacyFusioned(gift.fusioned, state.selectedFusioned)) return false
  if (!legacyExclusive(gift.themePack, state.selectedExclusive)) return false

  return true
}

function makeGift(overrides: Partial<EGOGiftListItem> & { id: string }): EGOGiftListItem {
  return {
    name: 'Fixture',
    tag: [],
    keyword: null,
    battleKeywordList: [],
    attributeType: 'CRIMSON',
    themePack: [],
    maxEnhancement: 0,
    ...overrides,
  }
}

const ITEMS: EGOGiftListItem[] = [
  makeGift({
    id: EGOGiftIdSchema.parse('9001'),
    tag: ['TIER_1', 'GIFT'],
    keyword: 'Burn',
    battleKeywordList: ['Poise'],
    attributeType: 'CRIMSON',
    themePack: ['1001'],
    fusioned: true,
  }),
  makeGift({
    id: EGOGiftIdSchema.parse('9002'),
    tag: ['TIER_3'],
    keyword: 'Bleed',
    battleKeywordList: ['Poise', 'Sinking'],
    attributeType: 'AMBER',
    themePack: ['1001', '1002'],
    hardOnly: true,
  }),
  makeGift({
    id: EGOGiftIdSchema.parse('9003'),
    tag: ['GIFT'],
    keyword: null,
    battleKeywordList: [],
    attributeType: 'CRIMSON',
    themePack: [],
    extremeOnly: true,
  }),
  makeGift({
    id: EGOGiftIdSchema.parse('9004'),
    tag: ['TIER_1', 'TIER_3'],
    keyword: 'Burn',
    battleKeywordList: undefined as unknown as string[],
    attributeType: undefined as unknown as string,
    themePack: undefined as unknown as string[],
    fusioned: false,
  }),
  makeGift({
    id: EGOGiftIdSchema.parse('9005'),
    tag: ['TIER_3'],
    keyword: 'None',
    battleKeywordList: ['Sinking'],
    attributeType: 'AMBER',
    themePack: ['1002'],
    hardOnly: true,
    extremeOnly: true,
  }),
  makeGift({
    id: EGOGiftIdSchema.parse('9006'),
    tag: [],
    keyword: 'Bleed',
    battleKeywordList: ['Poise'],
    attributeType: 'CRIMSON',
    themePack: ['1002', '1003'],
    fusioned: true,
  }),
]

const BASE_STATE: EGOGiftFacetState = {
  selectedKeywords: new Set(),
  selectedBattleKeywords: new Set(),
  selectedDifficulties: new Set(),
  selectedTiers: new Set(),
  selectedThemePacks: new Set(),
  selectedAttributeTypes: new Set(),
  selectedFusioned: new Set(),
  selectedExclusive: new Set(),
}

const STATES = enumerateSelectionStates(BASE_STATE, {
  selectedKeywords: [[], ['Burn'], ['None'], ['Burn', 'None']],
  selectedBattleKeywords: [[], ['Poise'], ['Poise', 'Sinking']],
  selectedDifficulties: [[], ['normal'], ['hard', 'extreme']],
  selectedTiers: [[], ['I'], ['I', 'III']],
  selectedThemePacks: [[], ['1001'], ['1001', '1002']],
  selectedAttributeTypes: [[], ['CRIMSON'], ['CRIMSON', 'AMBER']],
  selectedFusioned: [[], ['Y'], ['N'], ['Y', 'N']],
  selectedExclusive: [[], ['Y'], ['N'], ['Y', 'N']],
})

describe('EGO_GIFT_FACETS parity', () => {
  it('enumerates a broad selection matrix', () => {
    expect(STATES.length).toBeGreaterThan(10000)
  })

  it('survives the same ids as the pre-migration predicates', () => {
    const mismatches = findParityMismatches(
      ITEMS,
      STATES,
      (item, state) => legacyMatches(item, state),
      (item, state) => applyFacets(item, state, EGO_GIFT_FACETS),
    )
    expect(mismatches).toEqual([])
  })

  it('matches a null keyword only against None', () => {
    const noneOnly: EGOGiftFacetState = { ...BASE_STATE, selectedKeywords: new Set(['None']) }
    const survivors = ITEMS.filter((item) => applyFacets(item, noneOnly, EGO_GIFT_FACETS))
    expect(survivors.map((item) => item.id)).toEqual(['9003', '9005'])
  })

  it('drops gifts without an extractable tier', () => {
    const anyTier: EGOGiftFacetState = {
      ...BASE_STATE,
      selectedTiers: new Set<EGOGiftTier>(['I', 'II', 'III', 'IV', 'V', 'EX']),
    }
    const survivors = ITEMS.filter((item) => applyFacets(item, anyTier, EGO_GIFT_FACETS))
    expect(survivors.map((item) => item.id)).toEqual(['9001', '9002', '9004', '9005'])
  })

  it('drops gifts without an attribute type', () => {
    const anyAttribute: EGOGiftFacetState = {
      ...BASE_STATE,
      selectedAttributeTypes: new Set<EGOGiftAttributeType>(['CRIMSON', 'AMBER']),
    }
    const survivors = ITEMS.filter((item) => applyFacets(item, anyAttribute, EGO_GIFT_FACETS))
    expect(survivors.map((item) => item.id)).toEqual(['9001', '9002', '9003', '9005', '9006'])
  })
})
