import { PLANNER_CONFIG } from '@/lib/constants'
import type { PlannerConfig } from '../schemas/PlannerSchemas'

export function usePlannerConfig(): PlannerConfig {
  return PLANNER_CONFIG
}
