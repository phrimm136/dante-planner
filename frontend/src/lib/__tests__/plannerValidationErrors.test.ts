/**
 * plannerValidationErrors.test.ts
 *
 * Unit tests for the structured-error → i18n key mapping (toUserFriendlyError).
 */

import { describe, it, expect } from 'vitest'
import { toUserFriendlyError } from '../plannerValidationErrors'

// ============================================================================
// toUserFriendlyError
// ============================================================================

describe('toUserFriendlyError', () => {
  it('MISSING_TITLE → missingTitle key', () => {
    const result = toUserFriendlyError({ code: 'MISSING_TITLE', message: '' })
    expect(result.key).toBe('pages.plannerMD.publish.missingTitle')
  })

  it('FLOOR_MISSING_THEME_PACK → missingThemePack key', () => {
    const result = toUserFriendlyError({ code: 'FLOOR_MISSING_THEME_PACK', message: '' })
    expect(result.key).toBe('pages.plannerMD.publish.missingThemePack')
  })

  it('FLOOR_UNAFFORDABLE_GIFT → themePackEgoGiftInconsistency key with pack/gifts params', () => {
    const result = toUserFriendlyError({
      code: 'FLOOR_UNAFFORDABLE_GIFT',
      message: '',
      floorNumber: 3,
      context: { giftNames: 'Gift A, Gift B', themePackId: '1001' },
    })
    expect(result.key).toBe('pages.plannerMD.publish.themePackEgoGiftInconsistency')
    expect(result.params?.pack).toBe('1001')
    expect(result.params?.gifts).toBe('Gift A, Gift B')
  })

  it('GIFT_UNKNOWN_ID → unknownGiftId key with floor and gifts params', () => {
    const result = toUserFriendlyError({
      code: 'GIFT_UNKNOWN_ID',
      message: '',
      floorNumber: 2,
      context: { giftIds: ['2029', '2030'] },
    })
    expect(result.key).toBe('pages.plannerMD.validation.unknownGiftId')
    expect(result.params?.floor).toBe('2')
    expect(result.params?.gifts).toBe('2029, 2030')
  })

  it('DIFFICULTY_INVALID_FOR_CATEGORY → requiresHardMode key', () => {
    const result = toUserFriendlyError({ code: 'DIFFICULTY_INVALID_FOR_CATEGORY', message: '' })
    expect(result.key).toBe('pages.plannerMD.publish.requiresHardMode')
  })

  it('structural error codes → corruptedState key', () => {
    const structuralCodes = [
      'EQUIPMENT_MISSING_SINNER',
      'DEPLOYMENT_INVALID_INDEX',
      'SKILL_EA_MISSING_SINNER',
      'GIFT_DUPLICATE_ID',
      'BUFF_EXCEEDS_MAX',
      'START_GIFT_DUPLICATE_ID',
      'FLOOR_PREREQUISITE_VIOLATION',
      'FLOOR_DUPLICATE_THEME_PACK',
    ] as const

    for (const code of structuralCodes) {
      // Warning: each code is narrowed to a different error sub-type, so the
      // union-typed param cannot accept the bare { code, message } shape — cast
      // forces the generic default branch to be exercised.
      const result = toUserFriendlyError({ code: code as never, message: '' })
      expect(result.key).toBe('pages.plannerMD.validation.corruptedState')
    }
  })
})
