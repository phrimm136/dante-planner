import type { EntitySection } from '@/shared/filter'
import { EGOGiftIdSchema } from '@/shared/gameData'

import type { EGOGiftId } from '@/shared/gameData'
import { EGOGiftSpecListSchema, EGOGiftSpecSchema } from '../schemas/EGOGiftSchemas'
import type { EGOGiftSpec } from '../types/EGOGiftTypes'
import { EGO_GIFT_FACETS } from './egoGiftFilter'
import type { EGOGiftFacetState } from './egoGiftFilter'
import { toEGOGiftEntity } from './egoGiftEntity'

export const section: EntitySection<EGOGiftId, EGOGiftSpec, EGOGiftFacetState> = {
  name: 'egoGift',
  specFile: 'egoGiftSpecList.json',
  specListSchema: EGOGiftSpecListSchema,
  specEntrySchema: EGOGiftSpecSchema,
  idSchema: EGOGiftIdSchema,
  toEntity: toEGOGiftEntity,
  facets: EGO_GIFT_FACETS,
}
