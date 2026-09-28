/**
 * plannerValidationErrors.test.ts
 *
 * Unit tests for the structured-error → i18n key mapping (toUserFriendlyError).
 */

import { describe, it, expect } from 'vitest'
import { toUserFriendlyError } from '../plannerValidationErrors'
import { DUNGEON_IDX } from '@/shared/gameData'

// ============================================================================
// toUserFriendlyError
// ============================================================================

describe('toUserFriendlyError', () => {
  it('MISSING_TITLE → missingTitle key', () => {
    const result = toUserFriendlyError({ code: 'MISSING_TITLE', message: '' })
    expect(result.key).toBe('pages.plannerMD.publish.missingTitle')
  })

  it('FLOOR_MISSING_THEME_PACK → missingThemePack key', () => {
    const result = toUserFriendlyError({
      code: 'FLOOR_MISSING_THEME_PACK',
      message: '',
      field: 'floorSelections[4]',
    })
    expect(result.key).toBe('pages.plannerMD.publish.missingThemePack')
  })

  it('GIFT_NOT_AFFORDABLE → themePackEgoGiftInconsistency key with pack/gifts params', () => {
    const result = toUserFriendlyError({
      code: 'GIFT_NOT_AFFORDABLE',
      message: '',
      field: 'floorSelections[2].giftIds[0]',
      context: { giftName: 'Gift A', themePackId: '1001' },
    })
    expect(result).toEqual({
      key: 'pages.plannerMD.publish.themePackEgoGiftInconsistency',
      params: { pack: '1001', gifts: 'Gift A' },
    })
  })

  it('FLOOR_UNKNOWN_GIFT_ID → unknownGiftId key with floor and gifts params', () => {
    const result = toUserFriendlyError({
      code: 'FLOOR_UNKNOWN_GIFT_ID',
      message: '',
      field: 'floorSelections[1].giftIds',
      floorNumber: 2,
      context: { giftId: '2029' },
    })
    expect(result).toEqual({
      key: 'pages.plannerMD.validation.unknownGiftId',
      params: { floor: '2', gifts: '2029' },
    })
  })

  it.each([
    [[DUNGEON_IDX.HARD], 'pages.plannerMD.publish.requiresHardMode'],
    [[DUNGEON_IDX.EXTREME], 'pages.plannerMD.publish.requiresExtremeMode'],
    [[DUNGEON_IDX.NORMAL, DUNGEON_IDX.HARD], 'pages.plannerMD.validation.corruptedState'],
  ] as const)('VALUE_OUT_OF_RANGE on a floor allowing %j → %s', (allowedDifficulties, key) => {
    const result = toUserFriendlyError({
      code: 'VALUE_OUT_OF_RANGE',
      message: '',
      field: 'floorSelections[0].difficulty',
      context: { allowedDifficulties },
    })
    expect(result.key).toBe(key)
  })

  it('INVALID_SEQUENCE on a difficulty → normalAfterHard key', () => {
    const result = toUserFriendlyError({
      code: 'INVALID_SEQUENCE',
      message: '',
      field: 'floorSelections[1].difficulty',
    })
    expect(result.key).toBe('pages.plannerMD.publish.normalAfterHard')
  })

  it('INVALID_SEQUENCE on a floor → previousFloorNoThemePack key', () => {
    const result = toUserFriendlyError({
      code: 'INVALID_SEQUENCE',
      message: '',
      field: 'floorSelections[1]',
    })
    expect(result.key).toBe('pages.plannerMD.previousFloorNoThemePack')
  })

  it('THEME_PACK_UNKNOWN_ID → unknownThemePackId key with id param', () => {
    const result = toUserFriendlyError({
      code: 'THEME_PACK_UNKNOWN_ID',
      message: '',
      field: 'floorSelections[0].themePackId',
      context: { id: '9999' },
    })
    expect(result).toEqual({
      key: 'pages.plannerMD.validation.unknownThemePackId',
      params: { id: '9999' },
    })
  })

  it('KEYWORD_INVALID → invalidKeyword key with keyword param', () => {
    const result = toUserFriendlyError({
      code: 'KEYWORD_INVALID',
      message: '',
      context: { keyword: 'GhostKeyword' },
    })
    expect(result.key).toBe('pages.plannerMD.validation.invalidKeyword')
    expect(result.params?.keyword).toBe('GhostKeyword')
  })

  it('structural error codes → corruptedState key', () => {
    const structuralCodes = [
      'EQUIPMENT_MISSING_SINNER',
      'DEPLOYMENT_INVALID_INDEX',
      'SKILL_EA_MISSING_SINNER',
      'GIFT_DUPLICATE_ID',
      'BUFF_EXCEEDS_MAX',
      'START_GIFT_DUPLICATE_ID',
      'INVALID_FIELD_TYPE',
      'FLOOR_DUPLICATE_THEME_PACK',
      'DUPLICATE_VALUE',
    ] as const

    for (const code of structuralCodes) {
      // Warning: each code is narrowed to a different error sub-type, so the
      // union-typed param cannot accept the bare { code, message } shape.
      const result = toUserFriendlyError({ code: code as never, message: '' })
      expect(result.key).toBe('pages.plannerMD.validation.corruptedState')
    }
  })
})
