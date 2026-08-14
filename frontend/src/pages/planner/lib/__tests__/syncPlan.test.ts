import { describe, it, expect } from 'vitest'
import { categorizePlanner, categorizeSync, shouldPurgeLocal } from '../syncPlan'

import type { PlannerVerdict } from '../syncPlan'
import type { PlannerSummary } from '../../types/PlannerTypes'

const SAVED_AT = '2026-06-01T00:00:00.000Z'

function makeSummary(overrides: Partial<PlannerSummary> = {}): PlannerSummary {
  return {
    id: '11111111-2222-3333-4444-555555555555',
    title: 'Test',
    plannerType: 'MIRROR_DUNGEON',
    category: '5F',
    status: 'saved',
    lastModifiedAt: SAVED_AT,
    savedAt: SAVED_AT,
    syncVersion: 1,
    ...overrides,
  }
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
      local: [makeSummary({ id: 'a', syncVersion: undefined })],
      expected: { pull: ['a'], conflict: [], purge: [] },
    },
    {
      name: 'a previously-synced local row missing on the server is purged',
      server: [],
      local: [makeSummary({ id: 'a', status: 'saved', savedAt: SAVED_AT })],
      expected: { pull: [], conflict: [], purge: ['a'] },
    },
    {
      name: 'a local draft missing on the server is kept',
      server: [],
      local: [makeSummary({ id: 'a', status: 'draft', savedAt: SAVED_AT })],
      expected: { pull: [], conflict: [], purge: [] },
    },
    {
      name: 'all three partitions fill from one pass',
      server: [
        makeSummary({ id: 'pull-new' }),
        makeSummary({ id: 'pull-newer', syncVersion: 4 }),
        makeSummary({ id: 'conflicting', syncVersion: 4 }),
        makeSummary({ id: 'untouched', syncVersion: 2 }),
      ],
      local: [
        makeSummary({ id: 'pull-newer', syncVersion: 3, status: 'saved' }),
        makeSummary({ id: 'conflicting', syncVersion: 3, status: 'draft' }),
        makeSummary({ id: 'untouched', syncVersion: 2 }),
        makeSummary({ id: 'deleted-elsewhere', status: 'saved', savedAt: SAVED_AT }),
        makeSummary({ id: 'local-draft', status: 'draft', savedAt: null }),
      ],
      expected: {
        pull: ['pull-new', 'pull-newer'],
        conflict: ['conflicting'],
        purge: ['deleted-elsewhere'],
      },
    },
  ]

  it.each(cases)('$name', ({ server, local, expected }) => {
    const plan = categorizeSync(server, local)

    expect({
      pull: plan.pull.map((p) => p.id),
      conflict: plan.conflict.map((p) => p.id),
      purge: plan.purge.map((p) => p.id),
    }).toEqual(expected)
  })

  it('returns the server row for a pull and a conflict, and the local row for a purge', () => {
    const serverRow = makeSummary({ id: 'a', syncVersion: 4, title: 'Server' })
    const draftRow = makeSummary({ id: 'b', syncVersion: 4, title: 'Server draft side' })
    const localRow = makeSummary({ id: 'c', title: 'Local only', status: 'saved' })

    const plan = categorizeSync(
      [serverRow, draftRow],
      [
        makeSummary({ id: 'a', syncVersion: 1, status: 'saved', title: 'Local' }),
        makeSummary({ id: 'b', syncVersion: 1, status: 'draft', title: 'Local draft' }),
        localRow,
      ],
    )

    expect(plan.pull[0]).toBe(serverRow)
    expect(plan.conflict[0]).toBe(draftRow)
    expect(plan.purge[0]).toBe(localRow)
  })

  it('reads the inputs without mutating them', () => {
    const server = [makeSummary({ id: 'a', syncVersion: 4 })]
    const local = [makeSummary({ id: 'a', syncVersion: 1, status: 'draft' })]

    categorizeSync(server, local)

    expect(server).toEqual([makeSummary({ id: 'a', syncVersion: 4 })])
    expect(local).toEqual([makeSummary({ id: 'a', syncVersion: 1, status: 'draft' })])
  })
})

describe('shouldPurgeLocal', () => {
  it('purges when saved and savedAt is set (two witnesses of prior sync)', () => {
    expect(shouldPurgeLocal(makeSummary({ status: 'saved', savedAt: SAVED_AT }))).toBe(true)
  })

  it('keeps drafts even when they have prior saves', () => {
    // User started editing a previously-synced planner; their edits must not be wiped
    // just because the server lost the row.
    expect(shouldPurgeLocal(makeSummary({ status: 'draft', savedAt: SAVED_AT }))).toBe(false)
  })

  it('keeps never-synced drafts', () => {
    expect(shouldPurgeLocal(makeSummary({ status: 'draft', savedAt: null }))).toBe(false)
  })

  it('keeps inconsistent local state (saved but no savedAt)', () => {
    // Defensive: corrupt or pre-migration row that says saved but has no timestamp.
    // Erring toward preservation costs at most one repeated WARN; erring toward
    // purge could destroy user work.
    expect(shouldPurgeLocal(makeSummary({ status: 'saved', savedAt: null }))).toBe(false)
  })

  it('ignores syncVersion (relies on status + savedAt only)', () => {
    // syncVersion alone can't distinguish "first server save" from "never synced",
    // so it is deliberately not part of the predicate.
    expect(
      shouldPurgeLocal(makeSummary({ status: 'saved', savedAt: SAVED_AT, syncVersion: 1 })),
    ).toBe(true)
    expect(
      shouldPurgeLocal(makeSummary({ status: 'saved', savedAt: SAVED_AT, syncVersion: 99 })),
    ).toBe(true)
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
      local: makeSummary({ status: 'saved', syncVersion: undefined }),
      server: makeSummary({ syncVersion: 1 }),
      expected: 'pull',
    },
  ]

  it.each(cases)('$name', ({ local, server, expected }) => {
    expect(categorizePlanner(local, server)).toBe(expected)
  })
})
