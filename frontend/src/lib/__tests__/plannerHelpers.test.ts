/**
 * plannerHelpers.test.ts
 *
 * Unit tests for planner validation utility functions.
 * Tests both strict (publish) and non-strict (sync) validator modes,
 * mirroring the backend PlannerContentValidator strict/relaxed split.
 */

import { describe, it, expect } from 'vitest'
import {
  isGiftAffordableForThemePack,
  getUnaffordableGiftIds,
  validateEquipment,
  validateFloorThemePacksForSave,
  validatePlannerForSave,
  validatePlannerUserFriendly,
  toUserFriendlyError,
} from '../plannerHelpers'
import type { EGOGiftSpec } from '@/types/EGOGiftTypes'
import type { FloorThemeSelection } from '@/types/ThemePackTypes'
import type { MDPlannerContent } from '@/types/PlannerTypes'
import type { SinnerEquipment, SkillEAState } from '@/types/DeckTypes'

// ============================================================================
// Fixtures
// ============================================================================

function makeValidEquipment(): Record<string, SinnerEquipment> {
  const equipment: Record<string, SinnerEquipment> = {}
  for (let i = 1; i <= 12; i++) {
    const key = String(i).padStart(2, '0')
    equipment[key] = {
      identity: { id: `identity_${i}` },
      egos: { ZAYIN: { id: `ego_zayin_${i}` } },
    } as SinnerEquipment
  }
  return equipment
}

function makeValidSkillEAState(): Record<string, SkillEAState> {
  const state: Record<string, SkillEAState> = {}
  for (let i = 1; i <= 12; i++) {
    const key = String(i).padStart(2, '0')
    // Slots 0+1+2 must sum to 6
    state[key] = { 0: 3, 1: 2, 2: 1 } as unknown as SkillEAState
  }
  return state
}

/**
 * Creates a valid serialized floor selection array for the given floor count.
 * Theme pack IDs are auto-generated as unique strings ('1001', '1002', ...).
 * Difficulty is Hard (1) which satisfies all category requirements.
 */
function makeValidFloorSelections(
  count: number,
  opts: { difficulty?: number; startPackId?: number } = {}
) {
  const { difficulty = 1, startPackId = 1001 } = opts
  return Array.from({ length: count }, (_, i) => ({
    themePackId: String(startPackId + i),
    difficulty,
    giftIds: [] as string[],
  }))
}

/**
 * Creates a valid MDPlannerContent for the given category.
 * - 5F: 5 floors, Hard difficulty
 * - 10F: 10 floors, Hard difficulty
 * - 15F: floors 1-10 Hard, floors 11-15 Extreme (3)
 */
function makeValidContent(category: '5F' | '10F' | '15F' = '5F'): MDPlannerContent {
  const floorCountMap = { '5F': 5, '10F': 10, '15F': 15 }
  const count = floorCountMap[category]
  const floorSelections = makeValidFloorSelections(count)

  if (category === '15F') {
    for (let i = 10; i < 15; i++) {
      floorSelections[i].difficulty = 3 // EXTREME
    }
  }

  return {
    selectedKeywords: [],
    selectedBuffIds: [],
    selectedGiftKeyword: null,
    selectedGiftIds: [],
    observationGiftIds: [],
    comprehensiveGiftIds: [],
    equipment: makeValidEquipment(),
    deploymentOrder: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
    skillEAState: makeValidSkillEAState(),
    floorSelections,
    sectionNotes: {},
  }
}

// ============================================================================
// isGiftAffordableForThemePack
// ============================================================================

describe('isGiftAffordableForThemePack', () => {
  it('universal gift (empty themePack) is available in any theme pack', () => {
    const gift = { themePack: [] } as unknown as EGOGiftSpec
    expect(isGiftAffordableForThemePack(gift, '1024')).toBe(true)
    expect(isGiftAffordableForThemePack(gift, '1110')).toBe(true)
  })

  it('restricted gift is available only for its listed pack', () => {
    const gift = { themePack: ['1024'] } as unknown as EGOGiftSpec
    expect(isGiftAffordableForThemePack(gift, '1024')).toBe(true)
    expect(isGiftAffordableForThemePack(gift, '1110')).toBe(false)
  })
})

// ============================================================================
// getUnaffordableGiftIds
// ============================================================================

describe('getUnaffordableGiftIds', () => {
  const spec: Record<string, EGOGiftSpec> = {
    '9220': { themePack: ['1024'] } as unknown as EGOGiftSpec,
  }

  it('base gift ID on wrong pack returns that ID', () => {
    const result = getUnaffordableGiftIds(new Set(['9220']), '1110', spec)
    expect(result).toEqual(['9220'])
  })

  it('enhanced gift ID (19220) strips prefix to look up base ID 9220', () => {
    // '19220' → getBaseGiftId → '9220' → not in '1110' → unaffordable
    const result = getUnaffordableGiftIds(new Set(['19220']), '1110', spec)
    expect(result).toEqual(['19220'])
  })

  it('gift on correct pack returns empty array', () => {
    const result = getUnaffordableGiftIds(new Set(['9220']), '1024', spec)
    expect(result).toEqual([])
  })

  it('gift not in spec is silently skipped', () => {
    const result = getUnaffordableGiftIds(new Set(['9999']), '1110', spec)
    expect(result).toEqual([])
  })
})

// ============================================================================
// validateEquipment
// ============================================================================

describe('validateEquipment', () => {
  it('all 12 sinners with identity + ZAYIN returns no errors', () => {
    expect(validateEquipment(makeValidEquipment())).toHaveLength(0)
  })

  it('missing sinner key returns EQUIPMENT_MISSING_SINNER', () => {
    const equipment = makeValidEquipment()
    delete equipment['01']
    const errors = validateEquipment(equipment)
    expect(errors.some(e => e.code === 'EQUIPMENT_MISSING_SINNER')).toBe(true)
  })

  it('sinner without identity returns EQUIPMENT_MISSING_IDENTITY', () => {
    const equipment = makeValidEquipment()
    equipment['01'] = { identity: null, egos: { ZAYIN: { id: 'z' } } } as unknown as SinnerEquipment
    const errors = validateEquipment(equipment)
    expect(errors.some(e => e.code === 'EQUIPMENT_MISSING_IDENTITY')).toBe(true)
  })

  it('sinner without ZAYIN returns EQUIPMENT_MISSING_ZAYIN', () => {
    const equipment = makeValidEquipment()
    equipment['01'] = { identity: { id: 'i' }, egos: {} } as unknown as SinnerEquipment
    const errors = validateEquipment(equipment)
    expect(errors.some(e => e.code === 'EQUIPMENT_MISSING_ZAYIN')).toBe(true)
  })
})

// ============================================================================
// validateFloorThemePacksForSave
// ============================================================================

describe('validateFloorThemePacksForSave', () => {
  function makeFloors(packs: (string | null)[]): FloorThemeSelection[] {
    return packs.map(themePackId => ({
      themePackId,
      difficulty: 1,
      giftIds: new Set<string>(),
    }))
  }

  it('all packs present and unique returns no errors', () => {
    const floors = makeFloors(['1001', '1002', '1003', '1004', '1005'])
    expect(validateFloorThemePacksForSave(floors, 5)).toHaveLength(0)
  })

  it('missing pack on floor 2 returns FLOOR_MISSING_THEME_PACK', () => {
    const floors = makeFloors(['1001', null, '1003', '1004', '1005'])
    const errors = validateFloorThemePacksForSave(floors, 5)
    expect(errors.some(e => e.code === 'FLOOR_MISSING_THEME_PACK' && e.floorNumber === 2)).toBe(true)
  })

  it('duplicate pack returns FLOOR_DUPLICATE_THEME_PACK', () => {
    const floors = makeFloors(['1001', '1001', '1003', '1004', '1005'])
    const errors = validateFloorThemePacksForSave(floors, 5)
    expect(errors.some(e => e.code === 'FLOOR_DUPLICATE_THEME_PACK')).toBe(true)
  })

  it('floor 3 has pack but floor 2 missing returns FLOOR_PREREQUISITE_VIOLATION', () => {
    // Floor 1: pack, Floor 2: null, Floor 3: pack → Floor 3 fires prerequisite violation
    const floors = makeFloors(['1001', null, '1003'])
    const errors = validateFloorThemePacksForSave(floors, 3)
    expect(errors.some(e => e.code === 'FLOOR_PREREQUISITE_VIOLATION' && e.floorNumber === 3)).toBe(true)
  })
})

// ============================================================================
// validatePlannerForSave (strict mode)
// ============================================================================

describe('validatePlannerForSave (strict)', () => {
  it('valid publish-ready 5F content returns isValid: true', () => {
    const { isValid, errors } = validatePlannerForSave('My Plan', makeValidContent('5F'), '5F')
    expect(isValid).toBe(true)
    expect(errors).toHaveLength(0)
  })

  it('empty title returns MISSING_TITLE error', () => {
    const { isValid, errors } = validatePlannerForSave('', makeValidContent('5F'), '5F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'MISSING_TITLE')).toBe(true)
  })

  it('whitespace-only title returns MISSING_TITLE error', () => {
    const { isValid, errors } = validatePlannerForSave('   ', makeValidContent('5F'), '5F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'MISSING_TITLE')).toBe(true)
  })

  it('missing theme pack on active floor returns FLOOR_MISSING_THEME_PACK', () => {
    const content = makeValidContent('5F')
    content.floorSelections[2].themePackId = null
    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'FLOOR_MISSING_THEME_PACK')).toBe(true)
  })

  it('Normal difficulty on 10F floor returns DIFFICULTY_INVALID_FOR_CATEGORY', () => {
    const content = makeValidContent('10F')
    content.floorSelections[0].difficulty = 0 // NORMAL — invalid for 10F
    const { isValid, errors } = validatePlannerForSave('My Plan', content, '10F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'DIFFICULTY_INVALID_FOR_CATEGORY')).toBe(true)
  })
})

// ============================================================================
// validatePlannerUserFriendly (non-strict mode)
// ============================================================================

describe('validatePlannerUserFriendly (non-strict)', () => {
  it('valid sync-ready content with empty title and no theme packs returns null', () => {
    const content = makeValidContent('5F')
    for (const floor of content.floorSelections) floor.themePackId = null
    // Title is not part of MDPlannerContent — non-strict never checks it
    expect(validatePlannerUserFriendly(content, '5F')).toBeNull()
  })

  it('missing theme pack on last floor is allowed (returns null)', () => {
    const content = makeValidContent('5F')
    // Null the last floor only — no subsequent floor can trigger a prerequisite violation
    content.floorSelections[4].themePackId = null
    expect(validatePlannerUserFriendly(content, '5F')).toBeNull()
  })

  it('floor 3 has pack but floor 2 missing still returns prerequisite error', () => {
    const content = makeValidContent('5F')
    // Floor 2 has no pack, Floor 3 has one → FLOOR_PREREQUISITE_VIOLATION
    content.floorSelections[1].themePackId = null
    // floor 2 (index 1) missing, floor 3 (index 2) has pack
    const result = validatePlannerUserFriendly(content, '5F')
    expect(result?.key).toBe('pages.plannerMD.validation.corruptedState')
  })

  it('missing equipment sinner returns corruptedState i18n key', () => {
    const content = makeValidContent('5F')
    delete (content.equipment as Record<string, unknown>)['01']
    const result = validatePlannerUserFriendly(content, '5F')
    expect(result?.key).toBe('pages.plannerMD.validation.corruptedState')
  })

  it('unaffordable gift on floor with theme pack returns themePackEgoGiftInconsistency i18n key', () => {
    const content = makeValidContent('5F')
    // Floor 1 has themePackId '1001', add gift that's only for '1024'
    content.floorSelections[0].giftIds = ['9220']
    const spec: Record<string, EGOGiftSpec> = {
      '9220': { themePack: ['1024'] } as unknown as EGOGiftSpec,
    }
    const result = validatePlannerUserFriendly(content, '5F', spec)
    expect(result?.key).toBe('pages.plannerMD.publish.themePackEgoGiftInconsistency')
    expect(result?.params?.pack).toBe('1001') // floor 0's themePackId
  })
})

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
      const result = toUserFriendlyError({ code: code as never, message: '' })
      expect(result.key).toBe('pages.plannerMD.validation.corruptedState')
    }
  })
})
