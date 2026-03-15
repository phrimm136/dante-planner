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
  validateDeploymentOrder,
  validateSkillEAState,
  validateGiftIdArray,
  validateStartBuffIds,
  validateStartGiftSelection,
  validateFloorThemePacksForSave,
  validatePlannerForSave,
  validatePlannerUserFriendly,
  toUserFriendlyError,
} from '../plannerHelpers'
import type { FloorValidationError, DifficultyValidationError } from '../plannerHelpers'
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

  it('floor with duplicate gift IDs (array cast as Set) returns FLOOR_DUPLICATE_GIFT_ID', () => {
    // FLOOR_DUPLICATE_GIFT_ID is unreachable through validatePlannerForSave because
    // the Set deserialization step deduplicates giftIds before this function is called.
    // This test calls validateFloorThemePacksForSave directly with corrupted data.
    const floors: FloorThemeSelection[] = [{
      themePackId: '1001',
      difficulty: 1,
      giftIds: ['9001', '9001'] as unknown as Set<string>,
    }]
    const errors = validateFloorThemePacksForSave(floors, 1)
    expect(errors.some(e => e.code === 'FLOOR_DUPLICATE_GIFT_ID')).toBe(true)
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
      const result = toUserFriendlyError({ code: code as never, message: '' })
      expect(result.key).toBe('pages.plannerMD.validation.corruptedState')
    }
  })
})

// ============================================================================
// validateDeploymentOrder
// ============================================================================

describe('validateDeploymentOrder', () => {
  it('valid order [0..11] returns no errors', () => {
    expect(validateDeploymentOrder([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11])).toHaveLength(0)
  })

  it('empty array returns no errors', () => {
    expect(validateDeploymentOrder([])).toHaveLength(0)
  })

  it('index -1 returns DEPLOYMENT_INVALID_INDEX', () => {
    const errors = validateDeploymentOrder([-1])
    expect(errors.some(e => e.code === 'DEPLOYMENT_INVALID_INDEX')).toBe(true)
  })

  it('index 12 (one beyond max) returns DEPLOYMENT_INVALID_INDEX', () => {
    const errors = validateDeploymentOrder([12])
    expect(errors.some(e => e.code === 'DEPLOYMENT_INVALID_INDEX')).toBe(true)
  })

  it('multiple invalid indices produce one error each', () => {
    const errors = validateDeploymentOrder([-1, 12, 99])
    expect(errors.filter(e => e.code === 'DEPLOYMENT_INVALID_INDEX')).toHaveLength(3)
  })
})

// ============================================================================
// validateSkillEAState
// ============================================================================

describe('validateSkillEAState', () => {
  it('valid state for all 12 sinners returns no errors', () => {
    expect(validateSkillEAState(makeValidSkillEAState())).toHaveLength(0)
  })

  it('missing sinner key returns SKILL_EA_MISSING_SINNER listing which sinners', () => {
    const state = makeValidSkillEAState()
    delete state['01']
    const errors = validateSkillEAState(state)
    expect(errors.some(e => e.code === 'SKILL_EA_MISSING_SINNER')).toBe(true)
    const err = errors.find(e => e.code === 'SKILL_EA_MISSING_SINNER')
    expect((err?.context?.missingSinners as string[]).includes('01')).toBe(true)
  })

  it('invalid slot key "3" returns SKILL_EA_INVALID_SLOT', () => {
    const state = makeValidSkillEAState()
    state['01'] = { 0: 3, 1: 2, 3: 1 } as unknown as SkillEAState
    const errors = validateSkillEAState(state)
    expect(errors.some(e => e.code === 'SKILL_EA_INVALID_SLOT')).toBe(true)
  })

  it('skill EA totalling 7 instead of 6 returns SKILL_EA_INVALID_TOTAL', () => {
    const state = makeValidSkillEAState()
    state['01'] = { 0: 4, 1: 2, 2: 1 } as unknown as SkillEAState // 4+2+1=7
    const errors = validateSkillEAState(state)
    expect(errors.some(e => e.code === 'SKILL_EA_INVALID_TOTAL')).toBe(true)
  })
})

// ============================================================================
// validateGiftIdArray
// ============================================================================

describe('validateGiftIdArray', () => {
  it('unique gift IDs return no errors', () => {
    expect(validateGiftIdArray(['9001', '9002', '9003'], 'selectedGiftIds')).toHaveLength(0)
  })

  it('empty array returns no errors', () => {
    expect(validateGiftIdArray([], 'observationGiftIds')).toHaveLength(0)
  })

  it('duplicate gift ID returns GIFT_DUPLICATE_ID with fieldName in field', () => {
    const errors = validateGiftIdArray(['9001', '9002', '9001'], 'comprehensiveGiftIds')
    expect(errors.some(e => e.code === 'GIFT_DUPLICATE_ID')).toBe(true)
    expect(errors[0].field).toContain('comprehensiveGiftIds')
  })

  it('unknown gift ID returns GIFT_UNKNOWN_ID when egoGiftSpec is provided', () => {
    const spec: Record<string, EGOGiftSpec> = {
      '9001': { themePack: [] } as unknown as EGOGiftSpec,
    }
    const errors = validateGiftIdArray(['9001', '9999'], 'selectedGiftIds', spec)
    expect(errors).toHaveLength(1)
    expect(errors[0].code).toBe('GIFT_UNKNOWN_ID')
    expect(errors[0].context?.giftId).toBe('9999')
  })

  it('all valid gift IDs return no errors when egoGiftSpec is provided', () => {
    const spec: Record<string, EGOGiftSpec> = {
      '9001': { themePack: [] } as unknown as EGOGiftSpec,
      '9002': { themePack: [] } as unknown as EGOGiftSpec,
    }
    expect(validateGiftIdArray(['9001', '9002'], 'selectedGiftIds', spec)).toHaveLength(0)
  })

  it('enhanced gift ID resolves to base ID for existence check', () => {
    const spec: Record<string, EGOGiftSpec> = {
      '9220': { themePack: [] } as unknown as EGOGiftSpec,
    }
    expect(validateGiftIdArray(['19220'], 'selectedGiftIds', spec)).toHaveLength(0)
  })

  it('duplicate is reported before unknown for the same ID', () => {
    const spec: Record<string, EGOGiftSpec> = {}
    const errors = validateGiftIdArray(['9999', '9999'], 'selectedGiftIds', spec)
    expect(errors).toHaveLength(2)
    expect(errors[0].code).toBe('GIFT_UNKNOWN_ID')
    expect(errors[1].code).toBe('GIFT_DUPLICATE_ID')
  })

  it('skips existence check when egoGiftSpec is not provided', () => {
    expect(validateGiftIdArray(['9999'], 'selectedGiftIds')).toHaveLength(0)
  })
})

// ============================================================================
// validateStartBuffIds
// ============================================================================

describe('validateStartBuffIds', () => {
  it('valid buffs within limit return no errors', () => {
    // 100=base0, 201=base1, 302=base2 — all unique base IDs
    expect(validateStartBuffIds([100, 201, 302])).toHaveLength(0)
  })

  it('empty array returns no errors', () => {
    expect(validateStartBuffIds([])).toHaveLength(0)
  })

  it('11 buffs return BUFF_EXCEEDS_MAX', () => {
    // Only 10 unique base IDs (0-9) exist; 11 items forces repetition
    const ids = [100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 100]
    const errors = validateStartBuffIds(ids)
    expect(errors.some(e => e.code === 'BUFF_EXCEEDS_MAX')).toBe(true)
  })

  it('buff ID 110 (base 10 > max 9) returns BUFF_INVALID_FORMAT', () => {
    const errors = validateStartBuffIds([110]) // 110 % 100 = 10
    expect(errors.some(e => e.code === 'BUFF_INVALID_FORMAT')).toBe(true)
  })

  it('100 and 200 share base ID 0 and return BUFF_DUPLICATE_BASE_ID', () => {
    const errors = validateStartBuffIds([100, 200]) // both: id % 100 = 0
    expect(errors.some(e => e.code === 'BUFF_DUPLICATE_BASE_ID')).toBe(true)
  })
})

// ============================================================================
// validateStartGiftSelection
// ============================================================================

describe('validateStartGiftSelection', () => {
  it('no keyword and no gift IDs returns no errors', () => {
    expect(validateStartGiftSelection(null, [])).toHaveLength(0)
  })

  it('keyword with gift IDs returns no errors', () => {
    expect(validateStartGiftSelection('someKeyword', ['9001', '9002'])).toHaveLength(0)
  })

  it('no keyword but has gift IDs returns START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS', () => {
    const errors = validateStartGiftSelection(null, ['9001'])
    expect(errors.some(e => e.code === 'START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS')).toBe(true)
  })

  it('keyword with duplicate gift ID returns START_GIFT_DUPLICATE_ID', () => {
    const errors = validateStartGiftSelection('someKeyword', ['9001', '9001'])
    expect(errors.some(e => e.code === 'START_GIFT_DUPLICATE_ID')).toBe(true)
  })
})

// ============================================================================
// validatePlannerForSave – gift affordability (FLOOR_UNAFFORDABLE_GIFT)
// ============================================================================

describe('validatePlannerForSave – gift affordability', () => {
  const spec: Record<string, EGOGiftSpec> = {
    '9220': { themePack: ['1024'] } as unknown as EGOGiftSpec, // only available in pack 1024
    '9001': { themePack: [] } as unknown as EGOGiftSpec,       // universal
  }
  const i18n: Record<string, string> = { '9220': 'Dream-Eating Tapir' }

  it("gift '9220' is not affordable for theme pack '1110' → FLOOR_UNAFFORDABLE_GIFT", () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1110'
    content.floorSelections[0].giftIds = ['9220']

    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F', spec)
    expect(isValid).toBe(false)
    const err = errors.find(e => e.code === 'FLOOR_UNAFFORDABLE_GIFT') as FloorValidationError
    expect(err).toBeDefined()
    expect(err.floorNumber).toBe(1)
    expect(err.context?.themePackId).toBe('1110')
    expect((err.context?.giftIds as string[]).includes('9220')).toBe(true)
  })

  it("enhanced gift '19220' (level 1) not affordable for theme pack '1110' → FLOOR_UNAFFORDABLE_GIFT", () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1110'
    content.floorSelections[0].giftIds = ['19220'] // encoded: enhancement=1, base=9220

    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F', spec)
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'FLOOR_UNAFFORDABLE_GIFT')).toBe(true)
  })

  it("gift '9220' on its correct pack '1024' passes affordability", () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1024'
    content.floorSelections[0].giftIds = ['9220']

    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F', spec)
    expect(isValid).toBe(true)
    expect(errors.filter(e => e.code === 'FLOOR_UNAFFORDABLE_GIFT')).toHaveLength(0)
  })

  it('universal gift (empty themePack) passes affordability on any pack', () => {
    const content = makeValidContent('5F')
    content.floorSelections[2].giftIds = ['9001'] // pack '1003', universal gift

    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F', spec)
    expect(isValid).toBe(true)
    expect(errors.filter(e => e.code === 'FLOOR_UNAFFORDABLE_GIFT')).toHaveLength(0)
  })

  it('multiple unaffordable gifts on one floor produce a single error listing all', () => {
    const twoGiftSpec: Record<string, EGOGiftSpec> = {
      '9220': { themePack: ['1024'] } as unknown as EGOGiftSpec,
      '9221': { themePack: ['1024'] } as unknown as EGOGiftSpec,
    }
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1110'
    content.floorSelections[0].giftIds = ['9220', '9221']

    const { errors } = validatePlannerForSave('My Plan', content, '5F', twoGiftSpec)
    const affordErrors = errors.filter(e => e.code === 'FLOOR_UNAFFORDABLE_GIFT')
    expect(affordErrors).toHaveLength(1)
    expect((affordErrors[0] as FloorValidationError).context?.giftIds as string[]).toHaveLength(2)
  })

  it('unaffordable gifts on two separate floors produce one error per floor', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1110' // floor 1
    content.floorSelections[0].giftIds = ['9220']
    content.floorSelections[1].themePackId = '2000' // floor 2 (unique, not '1110')
    content.floorSelections[1].giftIds = ['9220']

    const { errors } = validatePlannerForSave('My Plan', content, '5F', spec)
    const affordErrors = errors.filter(e => e.code === 'FLOOR_UNAFFORDABLE_GIFT')
    expect(affordErrors).toHaveLength(2)
    expect((affordErrors[0] as FloorValidationError).floorNumber).toBe(1)
    expect((affordErrors[1] as FloorValidationError).floorNumber).toBe(2)
  })

  it('egoGiftI18n resolves gift ID to display name in error context', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1110'
    content.floorSelections[0].giftIds = ['9220']

    const { errors } = validatePlannerForSave('My Plan', content, '5F', spec, i18n)
    const err = errors.find(e => e.code === 'FLOOR_UNAFFORDABLE_GIFT') as FloorValidationError
    expect(err.context?.giftNames).toContain('Dream-Eating Tapir')
  })

  it('affordability check is skipped when egoGiftSpec is not provided', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].giftIds = ['9220'] // would fail if spec were provided

    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F') // no spec
    expect(isValid).toBe(true)
    expect(errors.filter(e => e.code === 'FLOOR_UNAFFORDABLE_GIFT')).toHaveLength(0)
  })
})

// ============================================================================
// validatePlannerForSave – gift existence (GIFT_UNKNOWN_ID)
// ============================================================================

describe('validatePlannerForSave – gift existence', () => {
  const spec: Record<string, EGOGiftSpec> = {
    '9001': { themePack: [] } as unknown as EGOGiftSpec,
    '9220': { themePack: ['1024'] } as unknown as EGOGiftSpec,
  }

  it('unknown floor gift ID returns GIFT_UNKNOWN_ID', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].giftIds = ['2029']

    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F', spec)
    expect(isValid).toBe(false)
    const err = errors.find(e => e.code === 'GIFT_UNKNOWN_ID') as FloorValidationError
    expect(err).toBeDefined()
    expect(err.floorNumber).toBe(1)
    expect((err.context?.giftIds as string[])).toContain('2029')
  })

  it('multiple unknown IDs on one floor produce a single GIFT_UNKNOWN_ID error listing all', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].giftIds = ['2029', '2030']

    const { errors } = validatePlannerForSave('My Plan', content, '5F', spec)
    const unknownErrors = errors.filter(e => e.code === 'GIFT_UNKNOWN_ID')
    expect(unknownErrors).toHaveLength(1)
    expect((unknownErrors[0] as FloorValidationError).context?.giftIds as string[]).toHaveLength(2)
  })

  it('unknown IDs on two separate floors produce one GIFT_UNKNOWN_ID error per floor', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].giftIds = ['2029']
    content.floorSelections[1].giftIds = ['2030']

    const { errors } = validatePlannerForSave('My Plan', content, '5F', spec)
    const unknownErrors = errors.filter(e => e.code === 'GIFT_UNKNOWN_ID')
    expect(unknownErrors).toHaveLength(2)
    expect((unknownErrors[0] as FloorValidationError).floorNumber).toBe(1)
    expect((unknownErrors[1] as FloorValidationError).floorNumber).toBe(2)
  })

  it('valid floor gift IDs return no GIFT_UNKNOWN_ID errors', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].giftIds = ['9001', '9220']

    const { errors } = validatePlannerForSave('My Plan', content, '5F', spec)
    expect(errors.filter(e => e.code === 'GIFT_UNKNOWN_ID')).toHaveLength(0)
  })

  it('existence check is skipped when egoGiftSpec is not provided', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].giftIds = ['2029']

    const { isValid } = validatePlannerForSave('My Plan', content, '5F')
    expect(isValid).toBe(true)
  })

  it('unknown gift in top-level selectedGiftIds returns GIFT_UNKNOWN_ID', () => {
    const content = makeValidContent('5F')
    content.selectedGiftKeyword = 'someKeyword'
    content.selectedGiftIds = ['9999']

    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F', spec)
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'GIFT_UNKNOWN_ID')).toBe(true)
  })
})

// ============================================================================
// validatePlannerForSave – individual validator propagation
// ============================================================================

describe('validatePlannerForSave – validator propagation', () => {
  it('invalid deployment index propagates DEPLOYMENT_INVALID_INDEX', () => {
    const content = makeValidContent('5F')
    content.deploymentOrder = [-1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]
    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'DEPLOYMENT_INVALID_INDEX')).toBe(true)
  })

  it('invalid skill EA total propagates SKILL_EA_INVALID_TOTAL', () => {
    const content = makeValidContent('5F')
    content.skillEAState['01'] = { 0: 4, 1: 2, 2: 1 } as unknown as SkillEAState // 4+2+1=7 ≠ 6
    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'SKILL_EA_INVALID_TOTAL')).toBe(true)
  })

  it('duplicate gift in selectedGiftIds propagates GIFT_DUPLICATE_ID', () => {
    const content = makeValidContent('5F')
    content.selectedGiftKeyword = 'someKeyword'
    content.selectedGiftIds = ['9001', '9001']
    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'GIFT_DUPLICATE_ID')).toBe(true)
  })

  it('11 start buffs propagate BUFF_EXCEEDS_MAX', () => {
    const content = makeValidContent('5F')
    content.selectedBuffIds = [100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 100]
    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'BUFF_EXCEEDS_MAX')).toBe(true)
  })

  it('gift IDs with no keyword propagate START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS', () => {
    const content = makeValidContent('5F')
    content.selectedGiftKeyword = null
    content.selectedGiftIds = ['9001']
    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS')).toBe(true)
  })

  it('missing equipment sinner propagates EQUIPMENT_MISSING_SINNER', () => {
    const content = makeValidContent('5F')
    delete (content.equipment as Record<string, unknown>)['01']
    const { isValid, errors } = validatePlannerForSave('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'EQUIPMENT_MISSING_SINNER')).toBe(true)
  })
})

// ============================================================================
// validatePlannerForSave – 15F difficulty rules
// ============================================================================

describe('validatePlannerForSave – 15F difficulty', () => {
  it('valid 15F (Hard floors 1-10, Extreme floors 11-15) returns isValid: true', () => {
    const { isValid } = validatePlannerForSave('My Plan', makeValidContent('15F'), '15F')
    expect(isValid).toBe(true)
  })

  it('floor 11 Hard (not Extreme) in 15F returns DIFFICULTY_INVALID_FOR_CATEGORY for floor 11', () => {
    const content = makeValidContent('15F')
    content.floorSelections[10].difficulty = 1 // index 10 = floor 11, must be Extreme (3)
    const { isValid, errors } = validatePlannerForSave('My Plan', content, '15F')
    expect(isValid).toBe(false)
    const err = errors.find(e => e.code === 'DIFFICULTY_INVALID_FOR_CATEGORY') as DifficultyValidationError
    expect(err).toBeDefined()
    expect(err.floorNumber).toBe(11)
  })

  it('floor 1 Normal (not Hard) in 15F returns DIFFICULTY_INVALID_FOR_CATEGORY', () => {
    const content = makeValidContent('15F')
    content.floorSelections[0].difficulty = 0 // Normal — floors 1-10 must be Hard
    const { isValid, errors } = validatePlannerForSave('My Plan', content, '15F')
    expect(isValid).toBe(false)
    expect(errors.some(e => e.code === 'DIFFICULTY_INVALID_FOR_CATEGORY')).toBe(true)
  })
})

// ============================================================================
// validatePlannerUserFriendly – additional cases
// ============================================================================

describe('validatePlannerUserFriendly – additional cases', () => {
  const spec: Record<string, EGOGiftSpec> = {
    '9220': { themePack: ['1024'] } as unknown as EGOGiftSpec,
  }
  const i18n: Record<string, string> = { '9220': 'Dream-Eating Tapir' }

  it('egoGiftI18n name appears in params.gifts for unaffordable gift', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1110'
    content.floorSelections[0].giftIds = ['9220']

    const result = validatePlannerUserFriendly(content, '5F', spec, i18n)
    expect(result?.key).toBe('pages.plannerMD.publish.themePackEgoGiftInconsistency')
    expect(result?.params?.gifts).toContain('Dream-Eating Tapir')
  })

  it('affordability check skipped without egoGiftSpec returns null', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].giftIds = ['9220'] // would fail with spec
    expect(validatePlannerUserFriendly(content, '5F')).toBeNull()
  })

  it('duplicate pack in non-strict mode returns corruptedState key', () => {
    const content = makeValidContent('5F')
    content.floorSelections[1].themePackId = content.floorSelections[0].themePackId // duplicate
    const result = validatePlannerUserFriendly(content, '5F')
    expect(result?.key).toBe('pages.plannerMD.validation.corruptedState')
  })

  it('unknown floor gift ID returns unknownGiftId i18n key', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].giftIds = ['2029']

    const result = validatePlannerUserFriendly(content, '5F', spec)
    expect(result?.key).toBe('pages.plannerMD.validation.unknownGiftId')
    expect(result?.params?.floor).toBe('1')
    expect(result?.params?.gifts).toContain('2029')
  })

  it('existence error is reported before affordability error', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1110'
    content.floorSelections[0].giftIds = ['2029', '9220']

    const result = validatePlannerUserFriendly(content, '5F', spec)
    expect(result?.key).toBe('pages.plannerMD.validation.unknownGiftId')
  })
})

// ============================================================================
// validatePlannerUserFriendly – gift affordability (FLOOR_UNAFFORDABLE_GIFT)
// ============================================================================

describe('validatePlannerUserFriendly – gift affordability', () => {
  const spec: Record<string, EGOGiftSpec> = {
    '9220': { themePack: ['1024'] } as unknown as EGOGiftSpec,
    '9221': { themePack: ['1024'] } as unknown as EGOGiftSpec,
    '9001': { themePack: [] } as unknown as EGOGiftSpec,
  }
  const i18n: Record<string, string> = { '9220': 'Dream-Eating Tapir', '9221': 'Pulsating Husk' }

  it('unaffordable gift returns themePackEgoGiftInconsistency with pack and gift name', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1110'
    content.floorSelections[0].giftIds = ['9220']

    const result = validatePlannerUserFriendly(content, '5F', spec, i18n)
    expect(result?.key).toBe('pages.plannerMD.publish.themePackEgoGiftInconsistency')
    expect(result?.params?.gifts).toContain('Dream-Eating Tapir')
    expect(result?.params?.pack).toBe('1110')
  })

  it('gift on correct pack returns null', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1024'
    content.floorSelections[0].giftIds = ['9220']

    expect(validatePlannerUserFriendly(content, '5F', spec)).toBeNull()
  })

  it('universal gift on any pack returns null', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].giftIds = ['9001']

    expect(validatePlannerUserFriendly(content, '5F', spec)).toBeNull()
  })

  it('multiple unaffordable gifts on one floor lists all names', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].themePackId = '1110'
    content.floorSelections[0].giftIds = ['9220', '9221']

    const result = validatePlannerUserFriendly(content, '5F', spec, i18n)
    expect(result?.key).toBe('pages.plannerMD.publish.themePackEgoGiftInconsistency')
    expect(result?.params?.gifts).toContain('Dream-Eating Tapir')
    expect(result?.params?.gifts).toContain('Pulsating Husk')
  })

  it('floor without theme pack skips affordability check', () => {
    const content = makeValidContent('5F')
    for (const floor of content.floorSelections) floor.themePackId = null
    content.floorSelections[0].giftIds = ['9220']

    expect(validatePlannerUserFriendly(content, '5F', spec)).toBeNull()
  })

  it('affordability check skipped without egoGiftSpec', () => {
    const content = makeValidContent('5F')
    content.floorSelections[0].giftIds = ['9220']

    expect(validatePlannerUserFriendly(content, '5F')).toBeNull()
  })
})
