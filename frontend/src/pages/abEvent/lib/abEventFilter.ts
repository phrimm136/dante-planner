/**
 * abEventFilter.ts
 *
 * Facet descriptors for the abnormality event browser, plus the per-item predicate the
 * grid's card slots subscribe through.
 */

import type { Facet } from '@/shared/filter'
import { applyFacets } from '@/shared/filter'
import type { FilterState } from '@/components/hooks/useSetFilters'
import type { AbEventSpecListEntry } from '../schemas/AbEventSchemas'

export interface AbEventFacetState {
  selectedEgoGifts: ReadonlySet<string>
  selectedThemePacks: ReadonlySet<string>
}

export const AB_EVENT_FACETS: readonly Facet<AbEventSpecListEntry, AbEventFacetState>[] = [
  { sel: (s) => s.selectedEgoGifts, get: (e) => e.relatedEgoGifts, mode: 'any' },
  { sel: (s) => s.selectedThemePacks, get: (e) => e.relatedThemePacks, mode: 'any' },
]

/** Whether one event survives the current facets. */
export function matchesAbEvent(
  entry: AbEventSpecListEntry,
  state: FilterState<AbEventFacetState>,
): boolean {
  return applyFacets(entry, state.values, AB_EVENT_FACETS)
}
