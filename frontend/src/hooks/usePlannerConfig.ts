import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'
import { PlannerConfigSchema, type PlannerConfig } from '@/schemas/PlannerSchemas'

/**
 * Query keys for planner config
 */
export const plannerConfigQueryKeys = {
  config: ['planner', 'config'] as const,
}

/**
 * Query options for planner config
 * Long staleTime since config rarely changes during a session
 */
function createPlannerConfigQueryOptions() {
  return queryOptions({
    queryKey: plannerConfigQueryKeys.config,
    queryFn: async (): Promise<PlannerConfig> => {
      const data = await ApiClient.get<PlannerConfig>('/api/planner/md/config')
      const result = PlannerConfigSchema.safeParse(data)
      if (!result.success) {
        console.error('Planner config validation failed:', result.error)
        throw new Error('Invalid planner config received from server')
      }
      return result.data
    },
    staleTime: 30 * 60 * 1000, // 30 minutes - config is relatively stable
  })
}

/**
 * Hook to get planner configuration from backend
 * Uses Suspense for SSR-compatible loading states
 *
 * Returns current MD version and available RR versions
 *
 * @example
 * ```tsx
 * function PlannerPage() {
 *   const config = usePlannerConfig()
 *   // config.mdCurrentVersion = 6
 *   // config.rrAvailableVersions = [1, 5]
 *
 *   return <StartBuffMiniCard version={config.mdCurrentVersion} ... />
 * }
 * ```
 */
export function usePlannerConfig(): PlannerConfig {
  const { data } = useSuspenseQuery(createPlannerConfigQueryOptions())
  return data
}
