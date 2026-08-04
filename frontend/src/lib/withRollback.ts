/**
 * Run a multi-step operation whose first step must be undone when a later step
 * fails.
 *
 * `rollback` runs only when `create` already succeeded, so no caller has to
 * carry a "did I create it yet?" flag. A failing rollback is logged and
 * swallowed — it must never replace the error that caused it.
 */
export async function withRollback(steps: {
  create: () => Promise<void>
  rest: () => Promise<void>
  rollback: () => Promise<void>
}): Promise<void> {
  await steps.create()

  try {
    await steps.rest()
  } catch (error) {
    try {
      await steps.rollback()
    } catch (rollbackError) {
      console.error('Rollback failed:', rollbackError)
    }
    throw error
  }
}
