import type { ConflictResolutionChoice } from '../types/PlannerTypes'

/**
 * Success toast for each conflict resolution, keyed so a new choice cannot ship
 * without one.
 */
export const CONFLICT_TOAST_KEY: Record<ConflictResolutionChoice, string> = {
  overwrite: 'pages.plannerMD.conflict.overwriteSuccess',
  discard: 'pages.plannerMD.conflict.discardSuccess',
  both: 'pages.plannerMD.conflict.keepBothSuccess',
}
