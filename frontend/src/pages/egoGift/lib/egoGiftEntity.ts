import { createEntityBuilder } from '@/shared/filter'
import { EGOGiftIdSchema } from '@/shared/gameData'

import type { EGOGiftId } from '@/shared/gameData'
import type { EGOGiftEntity, EGOGiftSpec } from '../types/EGOGiftTypes'

const UNKNOWN_GIFT_TIER_TAG = 'TIER_1'
const UNKNOWN_GIFT_ATTRIBUTE_TYPE = 'CRIMSON'

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
