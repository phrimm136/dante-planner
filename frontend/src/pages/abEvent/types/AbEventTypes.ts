/**
 * Abnormality Event Types
 *
 * Re-exports schema-derived types and defines composite types
 * not directly represented in JSON data files.
 */

import type { Entity } from '@/shared/filter'
import type { AbEventId } from '@/shared/gameData'
import type { AbEventSpec } from '../schemas/AbEventSchemas'

export type {
  AbEventSpec,
  AbEventSpecList,
  AbEventData,
  AbEventEffect,
  AbEventChoice,
  AbEventJudgement,
  AbEventResult,
  AbEventSelectionEvent,
  AbEventI18n,
  AbEventShared,
} from '../schemas/AbEventSchemas'

export type AbEventEntity = Entity<AbEventId, AbEventSpec>
