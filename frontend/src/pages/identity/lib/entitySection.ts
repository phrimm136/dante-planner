import type { EntitySection } from '@/shared/filter'
import { IdentityIdSchema } from '@/shared/gameData'

import type { IdentityId } from '@/shared/gameData'
import { IdentitySpecListSchema, IdentitySpecSchema } from '../schemas/IdentitySchemas'
import type { IdentitySpec } from '../types/IdentityTypes'
import { IDENTITY_FACETS } from './identityFilter'
import type { IdentityFacetState } from './identityFilter'
import { toIdentityEntity } from './identityEntity'

export const section: EntitySection<IdentityId, IdentitySpec, IdentityFacetState> = {
  name: 'identity',
  specFile: 'identitySpecList.json',
  specListSchema: IdentitySpecListSchema,
  specEntrySchema: IdentitySpecSchema,
  idSchema: IdentityIdSchema,
  toEntity: toIdentityEntity,
  facets: IDENTITY_FACETS,
}
