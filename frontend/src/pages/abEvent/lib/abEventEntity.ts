import { createEntityBuilder } from '@/shared/filter'
import { AbEventIdSchema } from '@/shared/gameData'

import type { AbEventId } from '@/shared/gameData'
import type { AbEventSpec } from '../types/AbEventTypes'

export const toAbEventEntity = createEntityBuilder<AbEventId, AbEventSpec>(AbEventIdSchema)
