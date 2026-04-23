/**
 * Abnormality Event Types
 *
 * Re-exports schema-derived types and defines composite types
 * not directly represented in JSON data files.
 */

export type {
  AbEventSpecListEntry,
  AbEventSpecList,
  AbEventData,
  AbEventEffect,
  AbEventChoice,
  AbEventJudgement,
  AbEventResult,
  AbEventSelectionEvent,
  AbEventI18n,
  AbEventShared,
} from '@/schemas/AbEventSchemas'

/** List item combining spec data with optional i18n name for rendering */
export interface AbEventListItem {
  id: string
  name?: string
  relatedEgoGifts: string[]
  relatedThemePacks: string[]
  hasImage: boolean
  illustId?: string
}
