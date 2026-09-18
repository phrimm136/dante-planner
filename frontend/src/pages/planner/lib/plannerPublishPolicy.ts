import type { PlannerValidationError } from './plannerValidationErrors'

export type PublishAction =
  | { kind: 'unpublish' }
  /** Publishing is refused; `error` is the first blocking validation failure. */
  | { kind: 'invalid'; error: PlannerValidationError }
  /** Publishing needs an upload the user has not opted into — confirm first. */
  | { kind: 'warnSyncDisabled' }
  /** Upload the current content, then publish. */
  | { kind: 'uploadThenPublish' }

export interface PublishDecision {
  isPublished: boolean | null | undefined
  validationErrors: readonly PlannerValidationError[]
  syncEnabled: boolean | null | undefined
}

export function decidePublishAction({
  isPublished,
  validationErrors,
  syncEnabled,
}: PublishDecision): PublishAction {
  if (isPublished) {
    return { kind: 'unpublish' }
  }

  const [firstError] = validationErrors
  if (firstError) {
    return { kind: 'invalid', error: firstError }
  }

  if (syncEnabled !== true) {
    return { kind: 'warnSyncDisabled' }
  }

  return { kind: 'uploadThenPublish' }
}
