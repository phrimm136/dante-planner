import { useState } from 'react'
import { PLANNER_CONFIG } from '@/lib/constants'
import { PlannerStoreContext, createPlannerStore } from '../usePlannerStore'
import type { ReactNode } from 'react'
import type { PlannerStoreSeed } from '../usePlannerStore'

interface SeededPlannerStoreProps {
  children: ReactNode
  mdVersion?: number
  identityMaxLevel?: number
  initialState?: PlannerStoreSeed | undefined
}

export function SeededPlannerStore({
  children,
  mdVersion = PLANNER_CONFIG.mdCurrentVersion,
  identityMaxLevel = 60,
  initialState,
}: SeededPlannerStoreProps) {
  const [store] = useState(() => createPlannerStore(mdVersion, identityMaxLevel, initialState))

  return <PlannerStoreContext.Provider value={store}>{children}</PlannerStoreContext.Provider>
}
