import type { EntitySection } from '@/shared/filter'
import { EGOIdSchema } from '@/shared/gameData'

import type { EGOId } from '@/shared/gameData'
import { EGOSpecListSchema, EGOSpecSchema } from '../schemas/EGOSchemas'
import type { EGOSpec } from '../types/EGOTypes'
import { EGO_FACETS } from './egoFilter'
import type { EGOFacetState } from './egoFilter'
import { toEGOEntity } from './egoEntity'

export const section: EntitySection<EGOId, EGOSpec, EGOFacetState> = {
  name: 'ego',
  specFile: 'egoSpecList.json',
  specListSchema: EGOSpecListSchema,
  specEntrySchema: EGOSpecSchema,
  idSchema: EGOIdSchema,
  toEntity: toEGOEntity,
  facets: EGO_FACETS,
}
