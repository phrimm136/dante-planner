import { MD_CATEGORY_COLORS, MD_CATEGORY_TEXT_COLORS } from '@/lib/constants'

import type { CSSProperties } from 'react'
import type { PlannerStatus } from '../types/PlannerTypes'

/**
 * How far a planner has travelled from "just edited" to "live on the community
 * list", as one value. The branches are mutually exclusive and ordered:
 * publication beats sync, sync beats local.
 */
export type SaveStatus =
  | 'draft'
  | 'saved'
  | 'unsynced'
  | 'synced'
  | 'published'
  | 'unpublishedChanges'

/** The persisted fields a save status is derived from. */
export interface SaveStatusSource {
  published?: boolean | null | undefined
  status: PlannerStatus
  savedAt?: string | null | undefined
}

/** Badge variant per save status, so a new status cannot ship unstyled. */
export const SAVE_STATUS_BADGE_VARIANT: Record<
  SaveStatus,
  'default' | 'secondary' | 'outline' | 'destructive'
> = {
  draft: 'secondary',
  saved: 'default',
  unsynced: 'secondary',
  synced: 'default',
  published: 'default',
  unpublishedChanges: 'destructive',
}

/**
 * Classify a planner's save state.
 *
 * `savedAt === null` counts as unsaved even when the status says otherwise:
 * a planner that has never been written has nothing to be in sync with.
 */
export function deriveSaveStatus(
  planner: SaveStatusSource,
  isAuthenticated: boolean,
  syncEnabled: boolean | null | undefined,
): SaveStatus {
  const hasPendingChanges = planner.status === 'draft' || planner.savedAt === null

  if (planner.published) {
    return planner.status === 'draft' ? 'unpublishedChanges' : 'published'
  }

  if (isAuthenticated && syncEnabled === true) {
    return hasPendingChanges ? 'unsynced' : 'synced'
  }

  return hasPendingChanges ? 'draft' : 'saved'
}

/**
 * Inline colours for a category badge; unknown categories fall back to the
 * surrounding element's own colours.
 */
export function categoryBadgeStyle(category: string): CSSProperties {
  return {
    backgroundColor: MD_CATEGORY_COLORS[category],
    color: MD_CATEGORY_TEXT_COLORS[category],
  }
}
