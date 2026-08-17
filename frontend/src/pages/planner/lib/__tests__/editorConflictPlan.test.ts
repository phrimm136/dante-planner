import { describe, it, expect, vi } from 'vitest'

import {
  forkedPlannerId,
  keepsLocal,
  needsServerAnchor,
  resolutionPlan,
} from '../editorConflictPlan'

import type { ConflictEffect } from '../conflictChoice'
import type { HeldResolution } from '../editorConflictPlan'
import type { AppError } from '@/lib/apiErrorClassifier'

const conflict: AppError = { kind: 'conflict', code: 'SYNC_CONFLICT', serverVersion: 7 }
const otherConflict: AppError = { kind: 'conflict', code: 'SYNC_CONFLICT', serverVersion: 9 }

const keepLocal: ConflictEffect = { kind: 'keepLocal' }
const adoptIncoming: ConflictEffect = { kind: 'adoptIncoming' }
const forkCopy: ConflictEffect = {
  kind: 'forkCopy',
  side: 'local',
  metadata: {
    id: 'copy-1',
    title: 'Planner (Copy)',
    status: 'saved',
    syncVersion: 1,
    deviceId: 'device-1',
    createdAt: '2026-01-01T00:00:00.000Z',
    lastModifiedAt: '2026-01-01T00:00:00.000Z',
    savedAt: '2026-01-01T00:00:00.000Z',
  },
}

function held(overrides: Partial<HeldResolution> = {}): HeldResolution {
  return { conflict, choice: 'both', plan: [forkCopy, adoptIncoming], ...overrides }
}

describe('resolutionPlan', () => {
  it('reuses the held plan, so a retry cannot mint a second copy', () => {
    const build = vi.fn(() => [keepLocal])

    expect(resolutionPlan(held(), conflict, 'both', build)).toEqual([forkCopy, adoptIncoming])
    expect(build).not.toHaveBeenCalled()
  })

  it('builds a new plan for a newer conflict', () => {
    const build = vi.fn(() => [keepLocal])

    expect(resolutionPlan(held(), otherConflict, 'both', build)).toEqual([keepLocal])
  })

  it('builds a new plan when the user changed their choice', () => {
    const build = vi.fn(() => [keepLocal])

    expect(resolutionPlan(held(), conflict, 'overwrite', build)).toEqual([keepLocal])
  })

  it('builds a plan when nothing is held', () => {
    expect(resolutionPlan(null, conflict, 'overwrite', () => [keepLocal])).toEqual([keepLocal])
  })
})

describe('needsServerAnchor', () => {
  it('anchors a push whose conflict reported no server version', () => {
    expect(needsServerAnchor(null, [keepLocal])).toBe(true)
    expect(needsServerAnchor(undefined, [forkCopy, keepLocal])).toBe(true)
  })

  it('leaves an anchored version alone', () => {
    expect(needsServerAnchor(7, [keepLocal])).toBe(false)
  })

  it('needs no anchor when nothing is pushed over the server', () => {
    expect(needsServerAnchor(null, [adoptIncoming])).toBe(false)
  })
})

describe('keepsLocal', () => {
  it('answers for the effect that writes the editor copy over the server', () => {
    expect(keepsLocal([forkCopy, keepLocal])).toBe(true)
    expect(keepsLocal([forkCopy, adoptIncoming])).toBe(false)
  })
})

describe('forkedPlannerId', () => {
  it('names the id the copy was minted under', () => {
    expect(forkedPlannerId([forkCopy, adoptIncoming])).toBe('copy-1')
  })

  it('answers null when the plan forks nothing', () => {
    expect(forkedPlannerId([keepLocal])).toBeNull()
  })
})
