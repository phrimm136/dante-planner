import { PLANNER_CONFIG } from '@/lib/constants'
import type { PlannerConfig } from '../schemas/PlannerSchemas'

/**
 * Hook to get planner configuration
 * Returns static config from constants (no backend dependency)
 *
 * Config values are synced between FE and BE via scripts/sync-planner-config.py
 *
 * @example
 * ```tsx
 * function PlannerPage() {
 *   const config = usePlannerConfig()
 *   // config.mdCurrentVersion = 7
 *   // config.rrAvailableVersions = [1, 5]
 * }
 * ```
 */
export function usePlannerConfig(): PlannerConfig {
  return PLANNER_CONFIG
}
