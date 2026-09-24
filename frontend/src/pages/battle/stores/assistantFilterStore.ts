import { createFilterStore } from '@/components/hooks/filterStore'
import type { AssistantFacetState } from '../lib/assistant/assistantFilter'

export const assistantFilterStore = createFilterStore<AssistantFacetState>({})
