import type { EntityMatcher, Facet } from '@/shared/filter'
import { createEntityMatcher } from '@/shared/filter'
import type { AssistantEntity } from '../../types/AssistantTypes'

export type AssistantFacetState = Record<string, never>

export const ASSISTANT_FACETS: readonly Facet<AssistantEntity, AssistantFacetState>[] = []

export const matchesAssistant: EntityMatcher<AssistantEntity, AssistantFacetState> =
  createEntityMatcher(ASSISTANT_FACETS)
