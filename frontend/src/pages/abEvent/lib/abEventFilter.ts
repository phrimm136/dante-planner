import type { EntityMatcher, Facet } from '@/shared/filter'
import { createEntityMatcher } from '@/shared/filter'
import type { AbEventNameList } from '../schemas/AbEventSchemas'
import type { AbEventEntity } from '../types/AbEventTypes'

export interface AbEventFacetState {
  selectedEgoGifts: ReadonlySet<string>
  selectedThemePacks: ReadonlySet<string>
}

export const AB_EVENT_FACETS: readonly Facet<AbEventEntity, AbEventFacetState>[] = [
  { sel: (s) => s.selectedEgoGifts, get: (e) => e.relatedEgoGifts, mode: 'any' },
  { sel: (s) => s.selectedThemePacks, get: (e) => e.relatedThemePacks, mode: 'any' },
]

export function buildAbEventSearchTerms(eventId: string, descs: AbEventNameList): string[] {
  return [(descs[eventId] ?? '').toLowerCase()]
}

export const matchesAbEvent: EntityMatcher<AbEventEntity, AbEventFacetState> =
  createEntityMatcher(AB_EVENT_FACETS)
