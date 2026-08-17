import type { Result } from './result'

/**
 * How a {@link withRollback} run ended.
 *
 * `undone` means nothing the run created survives — either `create` never
 * succeeded, or it did and the rollback cleaned it up. `undoFailed` means the
 * created thing is still there and the caller has to say so.
 */
export type RollbackOutcome<E> =
  | { kind: 'completed' }
  | { kind: 'undone'; error: E }
  | { kind: 'undoFailed'; error: E; rollbackError: E }

/**
 * Run a multi-step operation whose first step must be undone when a later step
 * fails.
 *
 * `rollback` runs only when `create` already succeeded, so no caller has to
 * carry a "did I create it yet?" flag.
 */
export async function withRollback<E>(steps: {
  create: () => Promise<Result<void, E>>
  rest: () => Promise<Result<void, E>>
  rollback: () => Promise<Result<void, E>>
}): Promise<RollbackOutcome<E>> {
  const created = await steps.create()
  if (!created.ok) return { kind: 'undone', error: created.error }

  const rest = await steps.rest()
  if (rest.ok) return { kind: 'completed' }

  const rolledBack = await steps.rollback()
  return rolledBack.ok
    ? { kind: 'undone', error: rest.error }
    : { kind: 'undoFailed', error: rest.error, rollbackError: rolledBack.error }
}
