import { describe, it, expect, vi } from 'vitest'
import { ok, err } from '@/lib/result'
import { INITIAL_SYNC_VERSION } from '@/lib/constants'
import { categorizePlanner, categorizeSync, settleTombstones } from '../syncPlan'
import { createSaveablePlanner } from '../saveablePlanner'

import type { PlannerVerdict, SyncOps } from '../syncPlan'
import type { LocalTombstone, PlannerSummary } from '../../types/PlannerTypes'

const SAVED_AT = '2026-06-01T00:00:00.000Z'

function makeSummary(overrides: Partial<PlannerSummary> = {}): PlannerSummary {
  return {
    id: '11111111-2222-3333-4444-555555555555',
    title: 'Test',
    plannerType: 'MIRROR_DUNGEON',
    category: '5F',
    status: 'saved',
    lastModifiedAt: SAVED_AT,
    syncVersion: 1,
    ...overrides,
  }
}

/** A row that never synced, so it carries no syncVersion at all. */
function makeUnsyncedSummary(overrides: Partial<PlannerSummary> = {}): PlannerSummary {
  const { syncVersion: _syncVersion, ...rest } = makeSummary(overrides)
  return rest
}

describe('categorizeSync', () => {
  const cases: {
    name: string
    server: PlannerSummary[]
    local: PlannerSummary[]
    expected: { pull: string[]; conflict: string[]; purge: string[] }
  }[] = [
    {
      name: 'empty inputs partition into nothing',
      server: [],
      local: [],
      expected: { pull: [], conflict: [], purge: [] },
    },
    {
      name: 'equal versions leave the row alone',
      server: [makeSummary({ id: 'a', syncVersion: 3 })],
      local: [makeSummary({ id: 'a', syncVersion: 3 })],
      expected: { pull: [], conflict: [], purge: [] },
    },
    {
      name: 'local ahead of the server leaves the row alone',
      server: [makeSummary({ id: 'a', syncVersion: 2 })],
      local: [makeSummary({ id: 'a', syncVersion: 5 })],
      expected: { pull: [], conflict: [], purge: [] },
    },
    {
      name: 'server newer over a saved local row pulls',
      server: [makeSummary({ id: 'a', syncVersion: 4 })],
      local: [makeSummary({ id: 'a', syncVersion: 3, status: 'saved' })],
      expected: { pull: ['a'], conflict: [], purge: [] },
    },
    {
      name: 'server newer over a local draft conflicts',
      server: [makeSummary({ id: 'a', syncVersion: 4 })],
      local: [makeSummary({ id: 'a', syncVersion: 3, status: 'draft' })],
      expected: { pull: [], conflict: ['a'], purge: [] },
    },
    {
      name: 'a planner missing locally is pulled whatever its version',
      server: [makeSummary({ id: 'a', syncVersion: 0 }), makeSummary({ id: 'b' })],
      local: [],
      expected: { pull: ['a', 'b'], conflict: [], purge: [] },
    },
    {
      name: 'a missing syncVersion counts as never synced on either side',
      server: [makeSummary({ id: 'a', syncVersion: 1 })],
      local: [makeUnsyncedSummary({ id: 'a' })],
      expected: { pull: ['a'], conflict: [], purge: [] },
    },
    {
      name: 'a local row missing on the server is kept, whatever its witnesses say',
      server: [],
      local: [makeSummary({ id: 'a', status: 'saved' })],
      expected: { pull: [], conflict: [], purge: [] },
    },
    {
      name: 'a server tombstone purges its saved local counterpart',
      server: [makeSummary({ id: 'a', deletedAt: SAVED_AT })],
      local: [makeSummary({ id: 'a', status: 'saved' })],
      expected: { pull: [], conflict: [], purge: ['a'] },
    },
    {
      name: 'a server tombstone keeps a local draft',
      server: [makeSummary({ id: 'a', deletedAt: SAVED_AT, syncVersion: 9 })],
      local: [makeSummary({ id: 'a', status: 'draft' })],
      expected: { pull: [], conflict: [], purge: [] },
    },
    {
      name: 'a tombstone with no local counterpart contributes nothing',
      server: [makeSummary({ id: 'a', deletedAt: SAVED_AT })],
      local: [],
      expected: { pull: [], conflict: [], purge: [] },
    },
    {
      name: 'all three partitions fill from one pass',
      server: [
        makeSummary({ id: 'pull-new' }),
        makeSummary({ id: 'pull-newer', syncVersion: 4 }),
        makeSummary({ id: 'conflicting', syncVersion: 4 }),
        makeSummary({ id: 'untouched', syncVersion: 2 }),
        makeSummary({ id: 'deleted-elsewhere', deletedAt: SAVED_AT }),
      ],
      local: [
        makeSummary({ id: 'pull-newer', syncVersion: 3, status: 'saved' }),
        makeSummary({ id: 'conflicting', syncVersion: 3, status: 'draft' }),
        makeSummary({ id: 'untouched', syncVersion: 2 }),
        makeSummary({ id: 'deleted-elsewhere', status: 'saved' }),
        makeSummary({ id: 'local-only', status: 'saved' }),
        makeSummary({ id: 'local-draft', status: 'draft' }),
      ],
      expected: {
        pull: ['pull-new', 'pull-newer'],
        conflict: ['conflicting'],
        purge: ['deleted-elsewhere'],
      },
    },
  ]

  it.each(cases)('$name', ({ server, local, expected }) => {
    const plan = categorizeSync(server, local, [])

    expect({
      pull: plan.pull.map((p) => p.id),
      conflict: plan.conflict.map((p) => p.id),
      purge: plan.purge.map((p) => p.id),
    }).toEqual(expected)
  })

  it('returns the server row for a pull and a conflict, and the local row for a purge', () => {
    const serverRow = makeSummary({ id: 'a', syncVersion: 4, title: 'Server' })
    const draftRow = makeSummary({ id: 'b', syncVersion: 4, title: 'Server draft side' })
    const localRow = makeSummary({ id: 'c', title: 'Deleted elsewhere', status: 'saved' })

    const plan = categorizeSync(
      [serverRow, draftRow, makeSummary({ id: 'c', deletedAt: SAVED_AT })],
      [
        makeSummary({ id: 'a', syncVersion: 1, status: 'saved', title: 'Local' }),
        makeSummary({ id: 'b', syncVersion: 1, status: 'draft', title: 'Local draft' }),
        localRow,
      ],
      [],
    )

    expect(plan.pull[0]).toBe(serverRow)
    expect(plan.conflict[0]).toBe(draftRow)
    expect(plan.purge[0]).toBe(localRow)
  })

  it('reads the inputs without mutating them', () => {
    const server = [makeSummary({ id: 'a', syncVersion: 4 })]
    const local = [makeSummary({ id: 'a', syncVersion: 1, status: 'draft' })]

    categorizeSync(server, local, [])

    expect(server).toEqual([makeSummary({ id: 'a', syncVersion: 4 })])
    expect(local).toEqual([makeSummary({ id: 'a', syncVersion: 1, status: 'draft' })])
  })
})

describe('categorizePlanner', () => {
  const cases: {
    name: string
    local: PlannerSummary | undefined
    server: PlannerSummary
    expected: PlannerVerdict
  }[] = [
    {
      name: 'no local row is pulled',
      local: undefined,
      server: makeSummary({ syncVersion: 1 }),
      expected: 'pull',
    },
    {
      name: 'a higher server version over a draft conflicts',
      local: makeSummary({ status: 'draft', syncVersion: 1 }),
      server: makeSummary({ syncVersion: 9 }),
      expected: 'conflict',
    },
    {
      name: 'a higher server version over a saved row pulls',
      local: makeSummary({ status: 'saved', syncVersion: 1 }),
      server: makeSummary({ syncVersion: 9 }),
      expected: 'pull',
    },
    {
      name: 'an equal server version is skipped',
      local: makeSummary({ syncVersion: 9 }),
      server: makeSummary({ syncVersion: 9 }),
      expected: 'skip',
    },
    {
      name: 'a lower server version is skipped',
      local: makeSummary({ syncVersion: 9 }),
      server: makeSummary({ syncVersion: 4 }),
      expected: 'skip',
    },
    {
      name: 'an equal server version over a draft is skipped, not a conflict',
      local: makeSummary({ status: 'draft', syncVersion: 3 }),
      server: makeSummary({ syncVersion: 3 }),
      expected: 'skip',
    },
    {
      name: 'a local row never synced falls to the version path',
      local: makeUnsyncedSummary({ status: 'saved' }),
      server: makeSummary({ syncVersion: 1 }),
      expected: 'pull',
    },
  ]

  it.each(cases)('$name', ({ local, server, expected }) => {
    expect(categorizePlanner(local, server)).toBe(expected)
  })
})

describe('purge against rows the server never acknowledged', () => {
  // The row exactly as a manual save produces it when no push happened: performSave builds with
  // status 'saved' before the sync gate — a signed-out or sync-off save, and a fork whose upload
  // failed, all share this shape.
  function localOnlySavedSummary(): PlannerSummary {
    const planner = createSaveablePlanner({
      state: {
        title: 'local only',
        category: '5F',
        selectedKeywords: new Set(),
        selectedBuffIds: new Set(),
        selectedGiftKeyword: null,
        selectedGiftIds: new Set(),
        observationGiftIds: new Set(),
        comprehensiveGiftIds: new Set(),
        equipment: {},
        deploymentOrder: [],
        skillEAState: {},
        floorSelections: [],
        sectionNotes: {},
      },
      plannerId: '99999999-8888-7777-6666-555555555555',
      schemaVersion: 1,
      contentVersion: 1,
      plannerType: 'MIRROR_DUNGEON',
      existingCreatedAt: null,
      existingSyncVersion: INITIAL_SYNC_VERSION,
      published: false,
      status: 'saved',
    })

    // The same field pick listLocal performs on a stored row.
    return {
      id: planner.metadata.id,
      title: planner.metadata.title,
      plannerType: planner.config.type,
      category: planner.config.category,
      status: planner.metadata.status,
      lastModifiedAt: planner.metadata.lastModifiedAt,
      syncVersion: planner.metadata.syncVersion,
    }
  }

  it('a saved row the server never acknowledged survives a pull pass that does not list it', () => {
    const plan = categorizeSync([], [localOnlySavedSummary()], [])
    expect(plan.purge).toEqual([])
  })
})

describe('categorizeSync against local tombstones', () => {
  const tombstone = (id: string, syncVersion: number): LocalTombstone => ({
    id,
    syncVersion,
    deletedAt: SAVED_AT,
  })

  const cases: {
    name: string
    server: PlannerSummary[]
    tombstones: LocalTombstone[]
    expected: { sweep: string[]; dropTombstone: string[]; pull: string[] }
  }[] = [
    {
      name: 'an unchanged server row is swept, not pulled',
      server: [makeSummary({ id: 'a', syncVersion: 3 })],
      tombstones: [tombstone('a', 3)],
      expected: { sweep: ['a'], dropTombstone: [], pull: [] },
    },
    {
      name: 'a newer server row drops the tombstone and pulls',
      server: [makeSummary({ id: 'a', syncVersion: 4 })],
      tombstones: [tombstone('a', 3)],
      expected: { sweep: [], dropTombstone: ['a'], pull: ['a'] },
    },
    {
      name: 'an unlisted row drops the tombstone',
      server: [],
      tombstones: [tombstone('a', 3)],
      expected: { sweep: [], dropTombstone: ['a'], pull: [] },
    },
    {
      name: 'a server-tombstoned row drops the local one',
      server: [makeSummary({ id: 'a', syncVersion: 3, deletedAt: SAVED_AT })],
      tombstones: [tombstone('a', 3)],
      expected: { sweep: [], dropTombstone: ['a'], pull: [] },
    },
  ]

  it.each(cases)('$name', ({ server, tombstones, expected }) => {
    const plan = categorizeSync(server, [], tombstones)

    expect({
      sweep: plan.sweep.map((t) => t.id),
      dropTombstone: plan.dropTombstone.map((t) => t.id),
      pull: plan.pull.map((p) => p.id),
    }).toEqual(expected)
  })
})

describe('settleTombstones', () => {
  const tombstone: LocalTombstone = { id: 'a', syncVersion: 3, deletedAt: SAVED_AT }

  function opsWith(deleteServer: SyncOps['deleteServer']) {
    const clearTombstone = vi.fn(async () => ok(undefined))
    const ops = { deleteServer, clearTombstone } as unknown as SyncOps
    return { ops, clearTombstone }
  }

  it.each([
    { name: 'a 204 clears the tombstone', result: ok(undefined) },
    { name: 'a 404 clears it', result: err({ kind: 'notFound' as const }) },
    { name: 'a 403 clears it', result: err({ kind: 'forbidden' as const, code: 'PLANNER_FORBIDDEN' }) },
  ])('$name', async ({ result }) => {
    const { ops, clearTombstone } = opsWith(async () => result)

    await settleTombstones({ sweep: [tombstone], dropTombstone: [] }, ops)

    expect(clearTombstone).toHaveBeenCalledWith('a')
  })

  it('keeps the tombstone when the server cannot be reached', async () => {
    const { ops, clearTombstone } = opsWith(async () => err({ kind: 'unknown' as const }))

    await settleTombstones({ sweep: [tombstone], dropTombstone: [] }, ops)

    expect(clearTombstone).not.toHaveBeenCalled()
  })

  it('drops without a server call', async () => {
    const deleteServer = vi.fn(async () => ok(undefined))
    const { ops, clearTombstone } = opsWith(deleteServer)

    await settleTombstones({ sweep: [], dropTombstone: [tombstone] }, ops)

    expect(deleteServer).not.toHaveBeenCalled()
    expect(clearTombstone).toHaveBeenCalledWith('a')
  })
})
