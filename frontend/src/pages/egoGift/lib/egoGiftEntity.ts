/** The single constructor turning a gift spec entry into an entity. */

import { createEntityBuilder } from '@/shared/filter'
import { EGOGiftIdSchema } from '@/shared/gameData'

import type { EGOGiftId } from '@/shared/gameData'
import type { EGOGiftEntity, EGOGiftSpec } from '../types/EGOGiftTypes'

/** What a placeholder claims when the catalogue cannot say. */
const UNKNOWN_GIFT_TIER_TAG = 'TIER_1'
const UNKNOWN_GIFT_ATTRIBUTE_TYPE = 'CRIMSON'

/**
 * Build an entity from a spec entry.
 *
 * @param id - Base gift ID, as the spec record keys it
 * @param spec - The gift's spec entry
 * @param name - Localized name; omitted where no name catalogue is loaded
 * @returns The entity, carrying every field the spec provides
 */
export const toEGOGiftEntity = createEntityBuilder<EGOGiftId, EGOGiftSpec>(EGOGiftIdSchema)

/**
 * A placeholder for a gift id the spec does not carry.
 *
 * A planner may hold an id from a content version this build predates, so the
 * id is kept verbatim: the placeholder exists for exactly the ids the gift
 * catalogue rejects, and re-parsing one here would throw instead of rendering.
 */
export function toUnknownEGOGiftEntity(id: string, name: string): EGOGiftEntity {
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
