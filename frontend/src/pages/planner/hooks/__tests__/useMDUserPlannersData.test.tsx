/**
 * useMDUserPlannersData.test.tsx
 *
 * Tests for user (personal) planners data hook.
 * Uses Vitest for testing query key structure.
 */

import { describe, it, expect } from 'vitest'
import {
  userPlannersQueryKeys,
  shouldPurgeLocal,
  adoptSyncedVersion,
} from '../useMDUserPlannersData'
import type { PlannerSummary, SaveablePlanner } from '../../types/PlannerTypes'

describe('userPlannersQueryKeys', () => {
  it('creates consistent base key', () => {
    const key = userPlannersQueryKeys.all
    expect(key).toEqual(['userPlanners'])
  })

  it('creates unique keys for different auth states', () => {
    const guestKey = userPlannersQueryKeys.list(false)
    const authKey = userPlannersQueryKeys.list(true)

    expect(guestKey).not.toEqual(authKey)
    expect(guestKey[2]).toEqual({ isAuthenticated: false })
    expect(authKey[2]).toEqual({ isAuthenticated: true })
  })

  it('includes auth state in list key for cache separation', () => {
    const key = userPlannersQueryKeys.list(true)

    expect(key[0]).toBe('userPlanners')
    expect(key[1]).toBe('list')
    expect(key[2]).toHaveProperty('isAuthenticated', true)
  })
})

describe('shouldPurgeLocal', () => {
  function makeSummary(overrides: Partial<PlannerSummary>): PlannerSummary {
    return {
      id: '11111111-2222-3333-4444-555555555555',
      title: 'Test',
      plannerType: 'MIRROR_DUNGEON',
      category: '5F',
      status: 'saved',
      lastModifiedAt: '2026-06-01T00:00:00.000Z',
      savedAt: '2026-06-01T00:00:00.000Z',
      syncVersion: 1,
      ...overrides,
    } as PlannerSummary
  }

  it('purges when saved and savedAt is set (two witnesses of prior sync)', () => {
    expect(
      shouldPurgeLocal(makeSummary({ status: 'saved', savedAt: '2026-06-01T00:00:00.000Z' })),
    ).toBe(true)
  })

  it('keeps drafts even when they have prior saves', () => {
    // User started editing a previously-synced planner; their edits must not be wiped
    // just because the server lost the row.
    expect(
      shouldPurgeLocal(makeSummary({ status: 'draft', savedAt: '2026-06-01T00:00:00.000Z' })),
    ).toBe(false)
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
      shouldPurgeLocal(
        makeSummary({ status: 'saved', savedAt: '2026-06-01T00:00:00.000Z', syncVersion: 1 }),
      ),
    ).toBe(true)
    expect(
      shouldPurgeLocal(
        makeSummary({ status: 'saved', savedAt: '2026-06-01T00:00:00.000Z', syncVersion: 99 }),
      ),
    ).toBe(true)
  })
})

describe('adoptSyncedVersion', () => {
  function makePlanner(syncVersion: number, title: string): SaveablePlanner {
    return {
      metadata: {
        id: '11111111-2222-3333-4444-555555555555',
        title,
        status: 'draft',
        syncVersion,
        savedAt: null,
      },
      config: { type: 'MIRROR_DUNGEON', category: '5F' },
      content: { title },
    } as unknown as SaveablePlanner
  }

  it('keeps the local content but adopts the server-assigned syncVersion', () => {
    const local = makePlanner(1, 'Local draft')
    const synced = makePlanner(7, 'Server echo')

    const saved = adoptSyncedVersion(local, synced)

    expect(saved.metadata.syncVersion).toBe(7)
    expect(saved.content).toEqual(local.content)
    expect(saved.metadata.title).toBe('Local draft')
  })

  it('marks the planner saved with a savedAt timestamp', () => {
    const saved = adoptSyncedVersion(makePlanner(1, 'A'), makePlanner(2, 'A'))

    expect(saved.metadata.status).toBe('saved')
    expect(saved.metadata.savedAt).not.toBeNull()
  })
})
