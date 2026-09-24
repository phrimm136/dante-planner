import type { EntitySection } from '@/shared/filter'
import { AssistantIdSchema } from '@/shared/gameData'

import type { AssistantId } from '@/shared/gameData'
import { AssistantSpecSchema, AssistantSpecListSchema } from '../../schemas/AssistantSchemas'
import type { AssistantSpec } from '../../types/AssistantTypes'
import { ASSISTANT_FACETS } from './assistantFilter'
import type { AssistantFacetState } from './assistantFilter'
import { toAssistantEntity } from './assistantEntity'

export const section: EntitySection<AssistantId, AssistantSpec, AssistantFacetState> = {
  name: 'battle/assistant',
  specFile: 'assistantSpecList.json',
  specListSchema: AssistantSpecListSchema,
  specEntrySchema: AssistantSpecSchema,
  idSchema: AssistantIdSchema,
  toEntity: toAssistantEntity,
  facets: ASSISTANT_FACETS,
}
