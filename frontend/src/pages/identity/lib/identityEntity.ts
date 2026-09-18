import { createEntityBuilder } from '@/shared/filter'
import { IdentityIdSchema } from '@/shared/gameData'

import type { IdentityId } from '@/shared/gameData'
import type { IdentitySpec } from '../types/IdentityTypes'

export const toIdentityEntity = createEntityBuilder<IdentityId, IdentitySpec>(IdentityIdSchema)
