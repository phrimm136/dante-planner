/**
 * Reporting for planner writes that run outside the conflict dialog's mount.
 *
 * `presentError` says nothing for a sync conflict because the resolution dialog
 * renders it. An imperative header action has no such dialog on screen, so it
 * has to name the stale version itself or the failure reaches nobody. Every
 * other rejection already has copy of its own.
 */

import { classifyAppError, isSyncConflict } from '@/lib/apiErrorClassifier'
import { showError, showErrorMessage } from '@/lib/errorPresentation'

const CHANGED_ELSEWHERE_KEY = 'planner:sync.changedElsewhere'

export function showSyncFailure(error: unknown): void {
  if (isSyncConflict(classifyAppError(error))) {
    showErrorMessage(CHANGED_ELSEWHERE_KEY)
    return
  }
  showError(error)
}
