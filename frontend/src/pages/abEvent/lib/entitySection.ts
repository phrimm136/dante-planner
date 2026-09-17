import type { EntitySection } from '@/shared/filter'
import { AbEventIdSchema } from '@/shared/gameData'

import type { AbEventId } from '@/shared/gameData'
import { AbEventSpecSchema, AbEventSpecListSchema } from '../schemas/AbEventSchemas'
import type { AbEventSpec } from '../types/AbEventTypes'
import { AB_EVENT_FACETS } from './abEventFilter'
import type { AbEventFacetState } from './abEventFilter'
import { toAbEventEntity } from './abEventEntity'

export const section: EntitySection<AbEventId, AbEventSpec, AbEventFacetState> = {
  name: 'abEvent',
  specFile: 'abEventSpecList.json',
  specListSchema: AbEventSpecListSchema,
  specEntrySchema: AbEventSpecSchema,
  idSchema: AbEventIdSchema,
  toEntity: toAbEventEntity,
  facets: AB_EVENT_FACETS,
}
