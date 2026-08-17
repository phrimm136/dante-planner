/** The single constructor turning a gift spec entry into a list item. */

import { EGOGiftIdSchema } from '@/shared/gameData'

import type { EGOGiftId } from '@/shared/gameData'
import type { EGOGiftListItem, EGOGiftSpec } from '../types/EGOGiftTypes'

/** What a placeholder claims when the catalogue cannot say. */
const UNKNOWN_GIFT_TIER_TAG = 'TIER_1'
const UNKNOWN_GIFT_ATTRIBUTE_TYPE = 'CRIMSON'

/**
 * Build a list item from a spec entry.
 *
 * @param id - Base gift ID, as the spec record keys it
 * @param spec - The gift's spec entry
 * @param name - Localized name; omitted where no name catalogue is loaded
 * @returns The list item, carrying every field the spec provides
 */
export function toGiftListItem(
  id: string,
  spec: EGOGiftSpec,
  name: string,
): EGOGiftListItem & { name: string }
export function toGiftListItem(id: string, spec: EGOGiftSpec, name?: string): EGOGiftListItem
export function toGiftListItem(id: string, spec: EGOGiftSpec, name?: string): EGOGiftListItem {
  return {
    id: EGOGiftIdSchema.parse(id),
    ...(name === undefined ? {} : { name }),
    tag: spec.tag as EGOGiftListItem['tag'],
    keyword: spec.keyword,
    battleKeywordList: spec.battleKeywordList ?? [],
    attributeType: spec.attributeType,
    themePack: spec.themePack,
    maxEnhancement: spec.maxEnhancement,
    ...(spec.recipe === undefined ? {} : { recipe: spec.recipe }),
    ...(spec.hardOnly === undefined ? {} : { hardOnly: spec.hardOnly }),
    ...(spec.extremeOnly === undefined ? {} : { extremeOnly: spec.extremeOnly }),
  }
}

/**
 * Build list items for every entry in a spec record.
 *
 * @param spec - Gift specs keyed by base gift ID
 * @param i18n - Gift names keyed by base gift ID; omitted where none is loaded
 * @returns One list item per spec entry, in record order
 */
export function toGiftListItems(
  spec: Record<string, EGOGiftSpec>,
  i18n?: Record<string, string>,
): EGOGiftListItem[] {
  return Object.entries(spec).map(([id, entry]) =>
    toGiftListItem(id, entry, i18n ? i18n[id] || id : undefined),
  )
}

/**
 * A placeholder for a gift id the spec does not carry.
 *
 * A planner may hold an id from a content version this build predates, so the
 * id is kept verbatim: the placeholder exists for exactly the ids the gift
 * catalogue rejects, and re-parsing one here would throw instead of rendering.
 */
export function toUnknownGiftListItem(id: string, name: string): EGOGiftListItem {
  return {
    id: id as EGOGiftId,
    name,
    tag: [UNKNOWN_GIFT_TIER_TAG],
    keyword: null,
    battleKeywordList: [],
    attributeType: UNKNOWN_GIFT_ATTRIBUTE_TYPE,
    themePack: [],
    maxEnhancement: 0,
  }
}
