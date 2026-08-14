import { describe, it, expect, vi } from 'vitest'
import { ok, err } from '@/lib/result'
import { buildSaveablePlanner } from '@/test-utils/fixtures'
import { planConflictResolution, interpretConflictPlan } from '../conflictChoice'

import type {
  ConflictInterpreterContext,
  ConflictOps,
  ConflictResolutionContext,
  PlannerConflict,
} from '../conflictChoice'
import type { ConflictResolutionChoice, SaveablePlanner } from '../../types/PlannerTypes'

const NOW = '2026-01-01T00:00:00.000Z'

function context(overrides: Partial<ConflictResolutionContext> = {}): ConflictResolutionContext {
  return {
    deviceId: 'device-1',
    now: NOW,
    newId: () => 'copy-id',
    copyTitle: (title) => `${title} [copy]`,
    ...overrides,
  }
}

const FORK_LOCAL: PlannerConflict = { forkSide: 'local', forkTitle: 'My Run' }
const FORK_INCOMING: PlannerConflict = { forkSide: 'incoming', forkTitle: 'My Run' }

describe('planConflictResolution', () => {
  const cases: {
    name: string
    choice: ConflictResolutionChoice
    conflict: PlannerConflict
    expected: string[]
  }[] = [
    {
      name: 'overwrite keeps local',
      choice: 'overwrite',
      conflict: FORK_LOCAL,
      expected: ['keepLocal'],
    },
    {
      name: 'overwrite keeps local whichever side forks',
      choice: 'overwrite',
      conflict: FORK_INCOMING,
      expected: ['keepLocal'],
    },
    {
      name: 'discard adopts incoming',
      choice: 'discard',
      conflict: FORK_LOCAL,
      expected: ['adoptIncoming'],
    },
    {
      name: 'discard adopts incoming whichever side forks',
      choice: 'discard',
      conflict: FORK_INCOMING,
      expected: ['adoptIncoming'],
    },
    {
      name: 'both forks local then leaves the original to the incoming side',
      choice: 'both',
      conflict: FORK_LOCAL,
      expected: ['forkCopy', 'adoptIncoming'],
    },
    {
      name: 'both forks incoming then leaves the original to the local side',
      choice: 'both',
      conflict: FORK_INCOMING,
      expected: ['forkCopy', 'keepLocal'],
    },
  ]

  it.each(cases)('$name', ({ choice, conflict, expected }) => {
    expect(planConflictResolution(choice, conflict, context()).map((e) => e.kind)).toEqual(expected)
  })

  it('builds the copy from the injected id, title and clock', () => {
    const [effect] = planConflictResolution('both', FORK_LOCAL, context())

    expect(effect).toEqual({
      kind: 'forkCopy',
      side: 'local',
      metadata: {
        id: 'copy-id',
        title: 'My Run [copy]',
        status: 'saved',
        syncVersion: 1,
        deviceId: 'device-1',
        createdAt: NOW,
        lastModifiedAt: NOW,
        savedAt: NOW,
      },
    })
  })

  it('titles the copy through the caller so the suffix stays translated', () => {
    const copyTitle = vi.fn((title: string) => `${title} (복사본)`)

    const [effect] = planConflictResolution('both', FORK_INCOMING, context({ copyTitle }))

    expect(copyTitle).toHaveBeenCalledWith('My Run')
    expect(effect).toMatchObject({ metadata: { title: 'My Run (복사본)' } })
  })

  it('names the side each fork copies, so the interpreter cannot copy the other one', () => {
    const [local] = planConflictResolution('both', FORK_LOCAL, context())
    const [incoming] = planConflictResolution('both', FORK_INCOMING, context())

    expect(local).toMatchObject({ kind: 'forkCopy', side: 'local' })
    expect(incoming).toMatchObject({ kind: 'forkCopy', side: 'incoming' })
  })

  it('mints an id only for the copy', () => {
    const newId = vi.fn(() => 'copy-id')

    planConflictResolution('overwrite', FORK_LOCAL, context({ newId }))
    planConflictResolution('discard', FORK_LOCAL, context({ newId }))
    expect(newId).not.toHaveBeenCalled()

    planConflictResolution('both', FORK_LOCAL, context({ newId }))
    expect(newId).toHaveBeenCalledTimes(1)
  })
})

describe('interpretConflictPlan', () => {
  const PLANNER_ID = '00000000-0000-4000-8000-000000000001'
  const LOCAL = buildSaveablePlanner({ metadata: { id: PLANNER_ID, title: 'My Run' } })
  const INCOMING = buildSaveablePlanner({
    metadata: { id: PLANNER_ID, title: 'My Run', syncVersion: 9 },
  })

  function interpreterContext(newId: () => string = () => 'copy-id'): ConflictInterpreterContext {
    return { deviceId: 'device-1', now: NOW, newId }
  }

  function operations(overrides: Partial<ConflictOps> = {}): ConflictOps {
    return {
      local: async () => ok(LOCAL),
      incoming: async () => ok(INCOMING),
      validate: () => null,
      saveLocal: async () => ok(undefined),
      deleteLocal: async () => ok(undefined),
      deleteRemote: async () => ok(undefined),
      sync: async (planner) => ok(planner),
      sanitizeTitle: (title) => title,
      ...overrides,
    }
  }

  /** The ids handed to `saveLocal`, in write order. */
  function savedIds(saveLocal: ReturnType<typeof vi.fn>): string[] {
    return saveLocal.mock.calls.map((call) => (call[0] as SaveablePlanner).metadata.id)
  }

  it('mints no identity while interpreting, so a retried plan writes one copy', async () => {
    const newId = vi.fn(() => 'copy-id')
    const ctx = interpreterContext(newId)
    const plan = planConflictResolution('both', FORK_LOCAL, { ...ctx, copyTitle: (t) => t })
    // Only the build may mint; everything after this point is a replay of it.
    newId.mockClear()

    const saveLocal = vi.fn(async (_planner: SaveablePlanner) => ok(undefined))
    const ops = operations({ saveLocal })

    expect(await interpretConflictPlan(plan, ops, ctx)).toEqual({ ok: true, value: undefined })
    expect(await interpretConflictPlan(plan, ops, ctx)).toEqual({ ok: true, value: undefined })

    expect(newId).not.toHaveBeenCalled()
    expect(new Set(savedIds(saveLocal).filter((id) => id === 'copy-id')).size).toBe(1)
    expect(savedIds(saveLocal)).toEqual(['copy-id', PLANNER_ID, 'copy-id', PLANNER_ID])
  })

  it('deletes the copy it saved when the copy fails to sync', async () => {
    const deleteLocal = vi.fn(async (_id: string) => ok(undefined))
    const ctx = interpreterContext()
    const plan = planConflictResolution('both', FORK_LOCAL, { ...ctx, copyTitle: (t) => t })

    const outcome = await interpretConflictPlan(
      plan,
      operations({ deleteLocal, sync: async () => err({ kind: 'quota' }) }),
      ctx,
    )

    expect(outcome).toEqual({ ok: false, error: { step: 'sync', error: { kind: 'quota' } } })
    expect(deleteLocal).toHaveBeenCalledWith('copy-id')
  })

  it('deletes the copy when a later effect fails, not only its own sync', async () => {
    const deleteLocal = vi.fn(async (_id: string) => ok(undefined))
    const ctx = interpreterContext()
    const plan = planConflictResolution('both', FORK_LOCAL, { ...ctx, copyTitle: (t) => t })

    const outcome = await interpretConflictPlan(
      plan,
      operations({ deleteLocal, incoming: async () => err({ kind: 'notFound' }) }),
      ctx,
    )

    expect(outcome).toEqual({ ok: false, error: { step: 'sync', error: { kind: 'notFound' } } })
    expect(deleteLocal).toHaveBeenCalledWith('copy-id')
  })

  it('deletes a copy the server already took when a later effect fails', async () => {
    // A copy left on the server is pulled straight back by the next sync, so a
    // local-only rollback undoes nothing.
    const deleteRemote = vi.fn(async (_id: string) => ok(undefined))
    const deleteLocal = vi.fn(async (_id: string) => ok(undefined))
    const ctx = interpreterContext()
    const plan = planConflictResolution('both', FORK_LOCAL, { ...ctx, copyTitle: (t) => t })

    const outcome = await interpretConflictPlan(
      plan,
      operations({ deleteRemote, deleteLocal, incoming: async () => err({ kind: 'notFound' }) }),
      ctx,
    )

    expect(outcome).toEqual({ ok: false, error: { step: 'sync', error: { kind: 'notFound' } } })
    expect(deleteLocal).toHaveBeenCalledWith('copy-id')
    expect(deleteRemote).toHaveBeenCalledWith('copy-id')
  })

  it('leaves the server alone when the caller uploaded nothing at all', async () => {
    // A signed-out resolution writes locally and syncs nothing, so there is no
    // server copy to undo — and a DELETE for an id the server never had would
    // downgrade a clean rollback to a failed one.
    const deleteRemote = vi.fn(async (_id: string) => ok(undefined))
    const ctx = interpreterContext()
    const plan = planConflictResolution('both', FORK_LOCAL, { ...ctx, copyTitle: (t) => t })

    const outcome = await interpretConflictPlan(
      plan,
      operations({
        deleteRemote,
        sync: async () => ok(null),
        incoming: async () => err({ kind: 'notFound' }),
      }),
      ctx,
    )

    expect(outcome).toEqual({ ok: false, error: { step: 'sync', error: { kind: 'notFound' } } })
    expect(deleteRemote).not.toHaveBeenCalled()
  })

  it('reports a rollback the server refused under its own step', async () => {
    const logged = vi.spyOn(console, 'error').mockImplementation(() => {})
    const ctx = interpreterContext()
    const plan = planConflictResolution('both', FORK_LOCAL, { ...ctx, copyTitle: (t) => t })
    const rollbackSteps: string[] = []

    const outcome = await interpretConflictPlan(
      plan,
      operations({
        incoming: async () => err({ kind: 'notFound' }),
        deleteRemote: async () => err({ kind: 'unavailable', scope: 'backend' }),
        deleteLocal: async (id: string) => {
          rollbackSteps.push(id)
          return err({ kind: 'quota' })
        },
      }),
      ctx,
    )

    // The user still sees what stopped the plan; the orphan is what the log carries.
    expect(outcome).toEqual({ ok: false, error: { step: 'sync', error: { kind: 'notFound' } } })
    expect(rollbackSteps).toEqual(['copy-id'])
    expect(logged).toHaveBeenCalled()
    logged.mockRestore()
  })

  it('stores what the sync did not upload, so a local-only resolution still lands', async () => {
    const saveLocal = vi.fn(async (_planner: SaveablePlanner) => ok(undefined))
    const ctx = interpreterContext()

    const outcome = await interpretConflictPlan(
      planConflictResolution('overwrite', FORK_LOCAL, { ...ctx, copyTitle: (t) => t }),
      operations({ saveLocal, sync: async () => ok(null) }),
      ctx,
    )

    expect(outcome).toEqual({ ok: true, value: undefined })
    const stored = saveLocal.mock.calls[0]![0]
    expect(stored.metadata).toMatchObject({ id: PLANNER_ID, status: 'saved', savedAt: NOW })
  })

  it('reads the local side when the resolution runs, not when the plan was built', async () => {
    const local = vi.fn(async () => ok(LOCAL))
    const ctx = interpreterContext()
    const plan = planConflictResolution('overwrite', FORK_LOCAL, { ...ctx, copyTitle: (t) => t })

    await interpretConflictPlan(plan, operations({ local }), ctx)
    await interpretConflictPlan(plan, operations({ local }), ctx)

    expect(local).toHaveBeenCalledTimes(2)
  })

  it('pushes nothing when the local side cannot be read', async () => {
    const sync = vi.fn(async (planner: SaveablePlanner) => ok(planner))
    const ctx = interpreterContext()

    const outcome = await interpretConflictPlan(
      planConflictResolution('overwrite', FORK_LOCAL, { ...ctx, copyTitle: (t) => t }),
      operations({ sync, local: async () => err({ kind: 'notFound' }) }),
      ctx,
    )

    expect(outcome).toEqual({
      ok: false,
      error: { step: 'saveLocal', error: { kind: 'notFound' } },
    })
    expect(sync).not.toHaveBeenCalled()
  })

  it('leaves the server alone when the copy never reached it', async () => {
    const deleteRemote = vi.fn(async (_id: string) => ok(undefined))
    const ctx = interpreterContext()
    const plan = planConflictResolution('both', FORK_LOCAL, { ...ctx, copyTitle: (t) => t })

    await interpretConflictPlan(
      plan,
      operations({ deleteRemote, sync: async () => err({ kind: 'quota' }) }),
      ctx,
    )

    expect(deleteRemote).not.toHaveBeenCalled()
  })

  it('reports the failure that stopped the plan, not the rollback that also failed', async () => {
    // The orphaned copy is visible in the planner list; the cause the user has to
    // act on is the one that stopped the resolution.
    const logged = vi.spyOn(console, 'error').mockImplementation(() => {})
    const ctx = interpreterContext()
    const plan = planConflictResolution('both', FORK_LOCAL, { ...ctx, copyTitle: (t) => t })

    const outcome = await interpretConflictPlan(
      plan,
      operations({
        sync: async () => err({ kind: 'quota' }),
        deleteLocal: async () => err({ kind: 'unknown' }),
      }),
      ctx,
    )

    expect(outcome).toEqual({ ok: false, error: { step: 'sync', error: { kind: 'quota' } } })
    logged.mockRestore()
  })

  it('stops before writing anything the validator rejects', async () => {
    const saveLocal = vi.fn(async (_planner: SaveablePlanner) => ok(undefined))
    const sync = vi.fn(async (planner: SaveablePlanner) => ok(planner))
    const ctx = interpreterContext()

    const outcome = await interpretConflictPlan(
      planConflictResolution('overwrite', FORK_LOCAL, { ...ctx, copyTitle: (t) => t }),
      operations({ saveLocal, sync, validate: () => ({ kind: 'validation', key: 'planner:bad' }) }),
      ctx,
    )

    expect(outcome).toEqual({
      ok: false,
      error: { step: 'validate', error: { kind: 'validation', key: 'planner:bad' } },
    })
    expect(saveLocal).not.toHaveBeenCalled()
    expect(sync).not.toHaveBeenCalled()
  })

  it('marks the kept side saved under the version the server acknowledged', async () => {
    const saveLocal = vi.fn(async (_planner: SaveablePlanner) => ok(undefined))
    const ctx = interpreterContext()

    await interpretConflictPlan(
      planConflictResolution('overwrite', FORK_LOCAL, { ...ctx, copyTitle: (t) => t }),
      operations({
        saveLocal,
        sync: async (planner) =>
          ok({ ...planner, metadata: { ...planner.metadata, syncVersion: 12 } }),
      }),
      ctx,
    )

    const stored = saveLocal.mock.calls[0][0] as SaveablePlanner
    expect(stored.metadata).toMatchObject({ status: 'saved', savedAt: NOW, syncVersion: 12 })
  })

  it('copies the side the plan names, not whichever side is cheapest to reach', async () => {
    // Copying local under an incoming-sided plan destroys the very version the
    // user asked to keep.
    const saveLocal = vi.fn(async (_planner: SaveablePlanner) => ok(undefined))
    const ctx = interpreterContext()
    const plan = planConflictResolution('both', FORK_INCOMING, { ...ctx, copyTitle: (t) => t })

    await interpretConflictPlan(plan, operations({ saveLocal }), ctx)

    const copy = saveLocal.mock.calls[0]![0]
    expect(copy.metadata.id).toBe('copy-id')
    expect(copy.metadata.syncVersion).toBe(1)
    // The incoming side carries syncVersion 9 before the fork metadata lands on it.
    expect(copy.content).toBe(INCOMING.content)
  })

  it('leaves the copy unpublished whatever the original was', async () => {
    const saveLocal = vi.fn(async (_planner: SaveablePlanner) => ok(undefined))
    const ctx = interpreterContext()
    const plan = planConflictResolution('both', FORK_LOCAL, { ...ctx, copyTitle: (t) => t })

    await interpretConflictPlan(
      plan,
      operations({
        saveLocal,
        local: async () =>
          ok(buildSaveablePlanner({ metadata: { id: PLANNER_ID, published: true } })),
      }),
      ctx,
    )

    const copy = saveLocal.mock.calls[0][0] as SaveablePlanner
    expect(copy.metadata.published).toBe(false)
  })
})
