import type { PlannerValidationError } from './plannerValidationErrors'

/** What pressing the publish toggle should do. */
export type PublishAction =
  /** Already published — flip the flag back, no upload. */
  | { kind: 'unpublish' }
  /** Publishing is refused; `error` is the first blocking validation failure. */
  | { kind: 'invalid'; error: PlannerValidationError }
  /** Publishing needs an upload the user has not opted into — confirm first. */
  | { kind: 'warnSyncDisabled' }
  /** Upload the current content, then publish. */
  | { kind: 'uploadThenPublish' }

export interface PublishDecision {
  isPublished: boolean | null | undefined
  /** Publish-time validation failures; empty for a planner that needs no validation. */
  validationErrors: readonly PlannerValidationError[]
  syncEnabled: boolean | null | undefined
}

/**
 * Decide the publish toggle's next step.
 *
 * Unpublishing skips validation and the sync warning: the row already exists on
 * the server and the toggle carries no content.
 */
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
