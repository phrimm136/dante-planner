import type { Entity } from '@/shared/filter'
import type { AssistantId } from '@/shared/gameData'
import type { AssistantSpec } from '../schemas/AssistantSchemas'

export type {
  AssistantSpec,
  AssistantSpecList,
  AssistantNameList,
} from '../schemas/AssistantSchemas'

export type AssistantEntity = Entity<AssistantId, AssistantSpec>
