import type { Result } from './result'

export type RollbackOutcome<E> =
  | { kind: 'completed' }
  | { kind: 'undone'; error: E }
  | { kind: 'undoFailed'; error: E; rollbackError: E }

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
