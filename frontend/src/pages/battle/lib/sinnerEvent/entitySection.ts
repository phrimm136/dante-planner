import type { EntitySection } from '@/shared/filter'
import { SinnerEventIdSchema } from '@/shared/gameData'

import type { SinnerEventId } from '@/shared/gameData'
import { SinnerEventSpecSchema, SinnerEventSpecListSchema } from '../../schemas/SinnerEventSchemas'
import type { SinnerEventSpec } from '../../types/SinnerEventTypes'
import { DRILL_DOWN_FACETS, type BattleDrillDownState } from '../battleFilter'
import { toSinnerEventEntity } from './sinnerEventEntity'

export const section: EntitySection<SinnerEventId, SinnerEventSpec, BattleDrillDownState> = {
  name: 'battle/sinnerEvent',
  specFile: 'sinnerEventSpecList.json',
  specListSchema: SinnerEventSpecListSchema,
  specEntrySchema: SinnerEventSpecSchema,
  idSchema: SinnerEventIdSchema,
  toEntity: toSinnerEventEntity,
  facets: DRILL_DOWN_FACETS,
}
