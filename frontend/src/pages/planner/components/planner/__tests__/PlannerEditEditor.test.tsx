/**
 * Parity tests for the editing-session derivation.
 *
 * `oracle` is the pre-split ladder transcribed verbatim from the `mode`-driven
 * component; every row asserts that the extracted `editorSessionFor` agrees
 * with it, so the split cannot have shifted a single boundary case.
 */

import { describe, it, expect } from 'vitest'
import { editorSessionFor } from '../PlannerEditEditor'
import type { SaveablePlanner, MDPlannerContent } from '../../../types/PlannerTypes'

const CURRENT_VERSION = 9

function plannerWith(metadata: Partial<SaveablePlanner['metadata']>): SaveablePlanner {
  return {
    metadata: {
      id: 'test-planner-123',
      title: 'Test',
      status: 'draft',
      schemaVersion: 2,
      contentVersion: 6,
      plannerType: 'MIRROR_DUNGEON',
      syncVersion: 1,
      createdAt: '2026-01-10T00:00:00Z',
      lastModifiedAt: '2026-01-10T00:00:00Z',
      savedAt: null,
      deviceId: 'device-123',
      ...metadata,
    },
    config: { type: 'MIRROR_DUNGEON', category: '5F' },
    content: {} as MDPlannerContent,
  }
}

/** The `mode === 'edit' && …` ladder the split replaced. */
function oracle(planner: SaveablePlanner, currentVersion: number) {
  return {
    contentVersion: planner.metadata.contentVersion
      ? planner.metadata.contentVersion
      : currentVersion,
    initialPlannerId: planner.metadata.id ? planner.metadata.id : undefined,
    initialSyncVersion:
      planner.metadata.syncVersion !== undefined ? planner.metadata.syncVersion : undefined,
    initialSavedAt: planner.metadata.lastModifiedAt ? planner.metadata.lastModifiedAt : undefined,
  }
}

const MATRIX: Array<[string, Partial<SaveablePlanner['metadata']>]> = [
  ['a fully populated planner', {}],
  ['contentVersion 0 (predates the field)', { contentVersion: 0 }],
  ['a blank id', { id: '' }],
  ['syncVersion 0', { syncVersion: 0 }],
  ['a blank lastModifiedAt', { lastModifiedAt: '' }],
  ['a high content version', { contentVersion: 42 }],
]

describe('editorSessionFor', () => {
  it.each(MATRIX)('matches the pre-split ladder for %s', (_label, metadata) => {
    const planner = plannerWith(metadata)

    expect(editorSessionFor(planner, CURRENT_VERSION)).toEqual(oracle(planner, CURRENT_VERSION))
  })

  it('keeps the stored content version rather than the current one', () => {
    const session = editorSessionFor(plannerWith({ contentVersion: 6 }), CURRENT_VERSION)

    expect(session.contentVersion).toBe(6)
  })

  it('falls back to the current content version only when none is stored', () => {
    const session = editorSessionFor(plannerWith({ contentVersion: 0 }), CURRENT_VERSION)

    expect(session.contentVersion).toBe(CURRENT_VERSION)
  })

  it('carries syncVersion 0 through instead of dropping it', () => {
    const session = editorSessionFor(plannerWith({ syncVersion: 0 }), CURRENT_VERSION)

    expect(session.initialSyncVersion).toBe(0)
  })
})
