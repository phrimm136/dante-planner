import { MD_CATEGORY_COLORS, MD_CATEGORY_TEXT_COLORS } from '@/lib/constants'

import type { CSSProperties } from 'react'
import type { PlannerStatus } from '../types/PlannerTypes'

export type SaveStatus =
  | 'draft'
  | 'saved'
  | 'unsynced'
  | 'synced'
  | 'published'
  | 'unpublishedChanges'

export interface SaveStatusSource {
  published?: boolean | null | undefined
  status: PlannerStatus
}

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

export function deriveSaveStatus(
  planner: SaveStatusSource,
  isAuthenticated: boolean,
  syncEnabled: boolean | null | undefined,
): SaveStatus {
  const hasPendingChanges = planner.status === 'draft'

  if (planner.published) {
    return planner.status === 'draft' ? 'unpublishedChanges' : 'published'
  }

  if (isAuthenticated && syncEnabled === true) {
    return hasPendingChanges ? 'unsynced' : 'synced'
  }

  return hasPendingChanges ? 'draft' : 'saved'
}

export function categoryBadgeStyle(category: string): CSSProperties {
  return {
    backgroundColor: MD_CATEGORY_COLORS[category],
    color: MD_CATEGORY_TEXT_COLORS[category],
  }
}
