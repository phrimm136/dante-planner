import type { EntitySection } from '@/shared/filter'
import { AssistantIdSchema } from '@/shared/gameData'

import type { AssistantId } from '@/shared/gameData'
import { AssistantSpecSchema, AssistantSpecListSchema } from '../../schemas/AssistantSchemas'
import type { AssistantSpec } from '../../types/AssistantTypes'
import { DRILL_DOWN_FACETS, type BattleDrillDownState } from '../battleFilter'
import { toAssistantEntity } from './assistantEntity'

export const section: EntitySection<AssistantId, AssistantSpec, BattleDrillDownState> = {
  name: 'battle/assistant',
  specFile: 'assistantSpecList.json',
  specListSchema: AssistantSpecListSchema,
  specEntrySchema: AssistantSpecSchema,
  idSchema: AssistantIdSchema,
  toEntity: toAssistantEntity,
  facets: DRILL_DOWN_FACETS,
}
