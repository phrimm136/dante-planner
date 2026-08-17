import type { AppError } from '@/lib/apiErrorClassifier'
import type { ConflictEffect } from './conflictChoice'
import type { ConflictResolutionChoice } from '../types/PlannerTypes'

/** The plan an editor built for the conflict it is still showing. */
export interface HeldResolution {
  conflict: AppError
  choice: ConflictResolutionChoice
  plan: ConflictEffect[]
}

/**
 * The plan this attempt runs, reusing the held one when it belongs to the same
 * conflict and the same choice.
 *
 * Planning again would mint a second identity, so a retried "keep both" would
 * write a second copy while the first attempt's copy is already rolled back or
 * still on disk. The conflict object is its own identity: a newer conflict is a
 * new object and earns a new plan.
 */
export function resolutionPlan(
  held: HeldResolution | null,
  conflict: AppError,
  choice: ConflictResolutionChoice,
  build: () => ConflictEffect[],
): ConflictEffect[] {
  if (held && held.conflict === conflict && held.choice === choice) return held.plan
  return build()
}

/** Whether the plan writes the editor's own copy over the server's. */
export function keepsLocal(plan: ConflictEffect[]): boolean {
  return plan.some((effect) => effect.kind === 'keepLocal')
}

/**
 * Whether the local sync version has to be anchored to the server's before the
 * plan runs. A conflict that reported no server version leaves it unanchored,
 * and the force push that follows would overwrite the server from behind.
 */
export function needsServerAnchor(
  serverVersion: number | null | undefined,
  plan: ConflictEffect[],
): boolean {
  return serverVersion == null && keepsLocal(plan)
}

/** The id a kept-both copy was minted under, or null when the plan forks nothing. */
export function forkedPlannerId(plan: ConflictEffect[]): string | null {
  const forked = plan.find(
    (effect): effect is Extract<ConflictEffect, { kind: 'forkCopy' }> => effect.kind === 'forkCopy',
  )
  return forked ? forked.metadata.id : null
}
