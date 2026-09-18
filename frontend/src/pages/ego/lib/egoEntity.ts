import { createEntityBuilder } from '@/shared/filter'
import { EGOIdSchema } from '@/shared/gameData'

import type { EGOId } from '@/shared/gameData'
import type { EGOSpec } from '../types/EGOTypes'

export const toEGOEntity = createEntityBuilder<EGOId, EGOSpec>(EGOIdSchema)
