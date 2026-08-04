import { describe, it, expect } from 'vitest'
import { MD_CATEGORY_COLORS, MD_CATEGORY_TEXT_COLORS } from '@/shared/gameData'
import { categoryBadgeStyle, deriveSaveStatus, SAVE_STATUS_BADGE_VARIANT } from '../plannerBadges'

import type { SaveStatus, SaveStatusSource } from '../plannerBadges'

const SAVED: SaveStatusSource = { published: false, status: 'saved', savedAt: '2026-01-01T00:00Z' }

/** The pre-dedup cascade, transcribed from PersonalPlannerCard. */
function oracle(
  planner: SaveStatusSource,
  isAuthenticated: boolean,
  syncEnabled: boolean | null | undefined,
): SaveStatus {
  if (planner.published === true) {
    return planner.status === 'draft' ? 'unpublishedChanges' : 'published'
  }
  if (isAuthenticated && syncEnabled === true) {
    return planner.status === 'draft' || planner.savedAt === null ? 'unsynced' : 'synced'
  }
  return planner.status === 'draft' || planner.savedAt === null ? 'draft' : 'saved'
}

describe('deriveSaveStatus', () => {
  it('agrees with the pre-dedup cascade across the whole input space', () => {
    for (const published of [true, false, null, undefined]) {
      for (const status of ['draft', 'saved'] as const) {
        for (const savedAt of ['2026-01-01T00:00Z', null, undefined]) {
          for (const isAuthenticated of [true, false]) {
            for (const syncEnabled of [true, false, null, undefined]) {
              const source: SaveStatusSource = { published, status, savedAt }

              expect(deriveSaveStatus(source, isAuthenticated, syncEnabled)).toBe(
                oracle(source, isAuthenticated, syncEnabled),
              )
            }
          }
        }
      }
    }
  })

  it('reports publication ahead of sync state', () => {
    expect(deriveSaveStatus({ ...SAVED, published: true }, true, true)).toBe('published')
    expect(deriveSaveStatus({ published: true, status: 'draft', savedAt: null }, true, true)).toBe(
      'unpublishedChanges',
    )
  })

  it('separates synced from unsynced only for an authenticated user with sync on', () => {
    expect(deriveSaveStatus(SAVED, true, true)).toBe('synced')
    expect(deriveSaveStatus(SAVED, true, false)).toBe('saved')
    expect(deriveSaveStatus(SAVED, false, true)).toBe('saved')
  })

  it('treats a never-written planner as unsaved whatever its status says', () => {
    expect(deriveSaveStatus({ ...SAVED, savedAt: null }, true, true)).toBe('unsynced')
    expect(deriveSaveStatus({ ...SAVED, savedAt: null }, false, false)).toBe('draft')
  })
})

describe('SAVE_STATUS_BADGE_VARIANT', () => {
  it('styles every status', () => {
    const statuses: SaveStatus[] = [
      'draft',
      'saved',
      'unsynced',
      'synced',
      'published',
      'unpublishedChanges',
    ]

    for (const status of statuses) {
      expect(SAVE_STATUS_BADGE_VARIANT[status]).toBeTruthy()
    }
  })
})

describe('categoryBadgeStyle', () => {
  it('reads the colours of a known category', () => {
    expect(categoryBadgeStyle('5F')).toEqual({
      backgroundColor: MD_CATEGORY_COLORS['5F'],
      color: MD_CATEGORY_TEXT_COLORS['5F'],
    })
  })

  it('leaves an unknown category unstyled rather than guessing', () => {
    expect(categoryBadgeStyle('NOT_A_CATEGORY')).toEqual({
      backgroundColor: undefined,
      color: undefined,
    })
  })
})
