/**
 * abEventFilter.ts
 *
 * Facet descriptors for the abnormality event browser, plus the per-item predicate the
 * grid's card slots subscribe through.
 */

import type { EntityMatcher, Facet } from '@/shared/filter'
import { createEntityMatcher } from '@/shared/filter'
import type { AbEventNameList, AbEventSpecListEntry } from '../schemas/AbEventSchemas'

export type AbEventListItem = readonly [eventId: string, entry: AbEventSpecListEntry]

export interface AbEventFacetState {
  selectedEgoGifts: ReadonlySet<string>
  selectedThemePacks: ReadonlySet<string>
}

export const AB_EVENT_FACETS: readonly Facet<AbEventListItem, AbEventFacetState>[] = [
  { sel: (s) => s.selectedEgoGifts, get: ([, e]) => e.relatedEgoGifts, mode: 'any' },
  { sel: (s) => s.selectedThemePacks, get: ([, e]) => e.relatedThemePacks, mode: 'any' },
]

/**
 * The lowercased string the search box matches an event on: its description.
 *
 * Depends only on the i18n payload, so a filter toggle never invalidates it.
 */
export function buildAbEventSearchTerms(eventId: string, descs: AbEventNameList): string[] {
  return [(descs[eventId] ?? '').toLowerCase()]
}

/** Whether one event survives the current facets and search query. */
export const matchesAbEvent: EntityMatcher<AbEventListItem, AbEventFacetState> =
  createEntityMatcher(AB_EVENT_FACETS)
