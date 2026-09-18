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
