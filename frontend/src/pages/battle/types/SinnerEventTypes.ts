import type { Entity } from '@/shared/filter'
import type { SinnerEventId } from '@/shared/gameData'
import type { SinnerEventSpec } from '../schemas/SinnerEventSchemas'

export type {
  SinnerEventSpec,
  SinnerEventSpecList,
  SinnerEventNameList,
} from '../schemas/SinnerEventSchemas'

export type SinnerEventEntity = Entity<SinnerEventId, SinnerEventSpec>
