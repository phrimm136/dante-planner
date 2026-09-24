import { createFilterStore } from '@/components/hooks/filterStore'
import type { SinnerEventFacetState } from '../lib/sinnerEvent/sinnerEventFilter'

export const sinnerEventFilterStore = createFilterStore<SinnerEventFacetState>({})
