import { createEntityBuilder } from '@/shared/filter'
import { SinnerEventIdSchema } from '@/shared/gameData'

import type { SinnerEventId } from '@/shared/gameData'
import type { SinnerEventSpec } from '../../types/SinnerEventTypes'

export const toSinnerEventEntity = createEntityBuilder<SinnerEventId, SinnerEventSpec>(
  SinnerEventIdSchema,
)
