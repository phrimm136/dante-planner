import type { EntityMatcher, Facet } from '@/shared/filter'
import { createEntityMatcher } from '@/shared/filter'
import type { SinnerEventEntity } from '../../types/SinnerEventTypes'

export type SinnerEventFacetState = Record<string, never>

export const SINNER_EVENT_FACETS: readonly Facet<SinnerEventEntity, SinnerEventFacetState>[] = []

export const matchesSinnerEvent: EntityMatcher<SinnerEventEntity, SinnerEventFacetState> =
  createEntityMatcher(SINNER_EVENT_FACETS)
