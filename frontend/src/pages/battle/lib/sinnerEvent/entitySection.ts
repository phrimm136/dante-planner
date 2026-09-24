import type { EntitySection } from '@/shared/filter'
import { SinnerEventIdSchema } from '@/shared/gameData'

import type { SinnerEventId } from '@/shared/gameData'
import { SinnerEventSpecSchema, SinnerEventSpecListSchema } from '../../schemas/SinnerEventSchemas'
import type { SinnerEventSpec } from '../../types/SinnerEventTypes'
import { SINNER_EVENT_FACETS } from './sinnerEventFilter'
import type { SinnerEventFacetState } from './sinnerEventFilter'
import { toSinnerEventEntity } from './sinnerEventEntity'

export const section: EntitySection<SinnerEventId, SinnerEventSpec, SinnerEventFacetState> = {
  name: 'battle/sinnerEvent',
  specFile: 'sinnerEventSpecList.json',
  specListSchema: SinnerEventSpecListSchema,
  specEntrySchema: SinnerEventSpecSchema,
  idSchema: SinnerEventIdSchema,
  toEntity: toSinnerEventEntity,
  facets: SINNER_EVENT_FACETS,
}
