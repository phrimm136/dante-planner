/**
 * plannerValidationErrors.test.ts
 *
 * Unit tests for the structured-error → i18n key mapping (toUserFriendlyError).
 */

import { describe, it, expect } from 'vitest'
import { contentPath, field, index } from '../validation/contentPath'
import { toUserFriendlyError } from '../plannerValidationErrors'

// ============================================================================
// toUserFriendlyError
// ============================================================================

describe('toUserFriendlyError', () => {
  it('MISSING_TITLE → missingTitle key', () => {
    const result = toUserFriendlyError({ code: 'MISSING_TITLE', message: '' })
    expect(result.key).toBe('pages.plannerMD.publish.missingTitle')
  })

  it.each(['INSUFFICIENT_FLOORS', 'MISSING_THEMEPACK'] as const)(
    '%s → missingThemePack key',
    (code) => {
      const result = toUserFriendlyError({
        code,
        message: '',
        path: contentPath(field('floorSelections'), index(4)),
      })
      expect(result.key).toBe('pages.plannerMD.publish.missingThemePack')
    },
  )

  it('UNAFFORDABLE_EGOGIFT → themePackEgoGiftInconsistency key with pack/gifts params', () => {
    const result = toUserFriendlyError({
      code: 'UNAFFORDABLE_EGOGIFT',
      message: '',
      path: contentPath(field('floorSelections'), index(2), field('giftIds'), index(0)),
      context: { giftName: 'Gift A', themePackId: '1001' },
    })
    expect(result).toEqual({
      key: 'pages.plannerMD.publish.themePackEgoGiftInconsistency',
      params: { pack: '1001', gifts: 'Gift A' },
    })
  })

  it('UNKNOWN_EGOGIFT_FLOOR → unknownGiftId key with floor and gifts params', () => {
    const result = toUserFriendlyError({
      code: 'UNKNOWN_EGOGIFT_FLOOR',
      message: '',
      path: contentPath(field('floorSelections'), index(1), field('giftIds'), index(0)),
      context: { giftId: '2029' },
    })
    expect(result).toEqual({
      key: 'pages.plannerMD.validation.unknownGiftId',
      params: { floor: '2', gifts: '2029' },
    })
  })

  it.each([
    ['INSUFFICIENT_DIFFICULTY_ENTRY', 'pages.plannerMD.publish.requiresHardMode'],
    ['OUT_OF_RANGE_DIFFICULTY_NOT_HARD', 'pages.plannerMD.publish.hardModeOnly'],
    ['OUT_OF_RANGE_DIFFICULTY_NOT_EXTREME', 'pages.plannerMD.publish.extremeModeOnly'],
    ['OUT_OF_RANGE_DIFFICULTY', 'pages.plannerMD.validation.corruptedState'],
  ] as const)('%s → %s', (code, key) => {
    const result = toUserFriendlyError({
      code,
      message: '',
      path: contentPath(field('floorSelections'), index(0), field('difficulty')),
    })
    expect(result.key).toBe(key)
  })

  it('OUT_OF_ORDER_DIFFICULTY → normalAfterHard key', () => {
    const result = toUserFriendlyError({
      code: 'OUT_OF_ORDER_DIFFICULTY',
      message: '',
      path: contentPath(field('floorSelections'), index(1), field('difficulty')),
    })
    expect(result.key).toBe('pages.plannerMD.publish.normalAfterHard')
  })

  it('OUT_OF_ORDER_THEMEPACK → previousFloorNoThemePack key', () => {
    const result = toUserFriendlyError({
      code: 'OUT_OF_ORDER_THEMEPACK',
      message: '',
      path: contentPath(field('floorSelections'), index(1)),
    })
    expect(result.key).toBe('pages.plannerMD.previousFloorNoThemePack')
  })

  it('UNKNOWN_THEMEPACK → unknownThemePackId key with id param', () => {
    const result = toUserFriendlyError({
      code: 'UNKNOWN_THEMEPACK',
      message: '',
      field: 'floorSelections[0].themePackId',
      context: { id: '9999' },
    })
    expect(result).toEqual({
      key: 'pages.plannerMD.validation.unknownThemePackId',
      params: { id: '9999' },
    })
  })

  it('UNKNOWN_KEYWORD → invalidKeyword key with keyword param', () => {
    const result = toUserFriendlyError({
      code: 'UNKNOWN_KEYWORD',
      message: '',
      context: { keyword: 'GhostKeyword' },
    })
    expect(result.key).toBe('pages.plannerMD.validation.invalidKeyword')
    expect(result.params?.keyword).toBe('GhostKeyword')
  })

  it('structural error codes → corruptedState key', () => {
    const structuralCodes = [
      'MISSING_SINNER',
      'OUT_OF_RANGE_DEPLOYMENT',
      'DUPLICATE_EGOGIFT',
      'EXCESS_STARTBUFF',
      'DUPLICATE_STARTGIFT',
      'INVALID_FIELD_TYPE',
      'DUPLICATE_THEMEPACK',
    ] as const

    for (const code of structuralCodes) {
      // Warning: each code is narrowed to a different error sub-type, so the
      // union-typed param cannot accept the bare { code, message } shape.
      const result = toUserFriendlyError({ code: code as never, message: '' })
      expect(result.key).toBe('pages.plannerMD.validation.corruptedState')
    }
  })
})
