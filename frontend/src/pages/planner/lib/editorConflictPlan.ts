import type { AppError } from '@/lib/apiErrorClassifier'
import type { ConflictEffect } from './conflictChoice'
import type { ConflictResolutionChoice } from '../types/PlannerTypes'

export interface HeldResolution {
  conflict: AppError
  choice: ConflictResolutionChoice
  plan: ConflictEffect[]
}

export function resolutionPlan(
  held: HeldResolution | null,
  conflict: AppError,
  choice: ConflictResolutionChoice,
  build: () => ConflictEffect[],
): ConflictEffect[] {
  if (held && held.conflict === conflict && held.choice === choice) return held.plan
  return build()
}

export function keepsLocal(plan: ConflictEffect[]): boolean {
  return plan.some((effect) => effect.kind === 'keepLocal')
}

export function needsServerAnchor(
  serverVersion: number | null | undefined,
  plan: ConflictEffect[],
): boolean {
  return serverVersion == null && keepsLocal(plan)
}

export function forkedPlannerId(plan: ConflictEffect[]): string | null {
  const forked = plan.find(
    (effect): effect is Extract<ConflictEffect, { kind: 'forkCopy' }> => effect.kind === 'forkCopy',
  )
  return forked ? forked.metadata.id : null
}
