/**
 * plannerValidation.test.ts
 *
 * Unit tests for planner validation functions.
 * Tests both strict (publish) and non-strict (sync) validator modes,
 * mirroring the backend PlannerContentValidator strict/relaxed split.
 */

import { describe, it, expect } from 'vitest'
import {
  validateEquipment,
  validateDeploymentOrder,
  validateSkillEAState,
  validateGiftIdArray,
  validateStartBuffIds,
  validateStartGiftSelection,
  validatePlannerForPublish,
  validatePlannerForDraftSave,
  validatePlannerForImport,
  validateNoteSizes,
  validateSelectedKeywords,
} from '../plannerValidation'
import { normalizePlannerIds } from '../plannerIdNormalize'
import type { IdMigrationTable } from '../idMigrationTable'
import type { PlannerIdRegistry, PlannerValidationResult } from '../plannerValidation'
import { toUserFriendlyError } from '../plannerValidationErrors'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import { calculateNoteByteLength } from '@/shared/noteEditor'
import type {
  FloorUnknownGiftValidationError,
  GiftNotAffordableValidationError,
  PlannerValidationError,
} from '../plannerValidationErrors'
import type { EGOGiftSpec } from '@/pages/egoGift'
import type { DungeonIdx, ThemePackId } from '@/shared/gameData'
import type { MDPlannerContent, SerializableFloorSelection } from '../../types/PlannerTypes'
import type { SinnerEquipment, SkillEAState } from '../../types/DeckTypes'
import { ThemePackIdSchema } from '@/shared/gameData'
import type { EncodedGiftId } from '@/shared/gameData'
import { asEGOId, asEncodedGiftId, asIdentityId, buildSaveablePlanner } from '@/test-utils/fixtures'

const ENCODED_9001 = asEncodedGiftId('9001')
const ENCODED_9220 = asEncodedGiftId('9220')
const ENCODED_9221 = asEncodedGiftId('9221')
const ENCODED_19220 = asEncodedGiftId('19220')
/** Decodes, but no spec in this file carries it: the unknown-gift id. */
const ENCODED_9999 = asEncodedGiftId('9999')
/** A second unknown-to-spec id, for the two-unknowns-on-one-floor cases. */
const ENCODED_9998 = asEncodedGiftId('9998')

// ============================================================================
// Fixtures
// ============================================================================

/** The element at `index`, failing with the actual length instead of yielding undefined. */
function at<T>(items: readonly T[], index: number): T {
  const item = items[index]
  if (item === undefined) {
    throw new Error(`expected an element at index ${index}, got ${items.length}`)
  }
  return item
}

/** Each error as [code, field], the shape the floor-rule corpus records. */
function codesAndFields(result: PlannerValidationResult): [string, string | undefined][] {
  return result.errors.map((e) => [e.code, e.field])
}

function onlyCode<C extends PlannerValidationError['code']>(
  result: PlannerValidationResult,
  code: C,
): Extract<PlannerValidationError, { code: C }>[] {
  return result.errors.filter(
    (e): e is Extract<PlannerValidationError, { code: C }> => e.code === code,
  )
}

/** The floor at `index`, failing loudly if the fixture is shorter than the test assumes. */
function floorAt(content: MDPlannerContent, index: number): SerializableFloorSelection {
  return at(content.floorSelections, index)
}

/** Builds a genuinely valid EGOGiftSpec restricted to the given theme packs. */
function makeGiftSpec(themePack: string[]): EGOGiftSpec {
  return {
    tag: ['TIER_1'],
    keyword: null,
    battleKeywordList: [],
    attributeType: '',
    themePack,
    maxEnhancement: 0,
  }
}

function makeValidEquipment(): Record<string, SinnerEquipment> {
  const equipment: Record<string, SinnerEquipment> = {}
  for (let i = 1; i <= 12; i++) {
    const key = String(i).padStart(2, '0')
    equipment[key] = {
      identity: { id: asIdentityId(`1${key}01`), uptie: 1, level: 1 },
      egos: { ZAYIN: { id: asEGOId(`2${key}01`), threadspin: 1 } },
    }
  }
  return equipment
}

function makeValidSkillEAState(): Record<string, SkillEAState> {
  const state: Record<string, SkillEAState> = {}
  for (let i = 1; i <= 12; i++) {
    const key = String(i).padStart(2, '0')
    // Slots 0+1+2 must sum to 6
    state[key] = { 0: 3, 1: 2, 2: 1 }
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
  opts: { difficulty?: DungeonIdx; startPackId?: number } = {},
): SerializableFloorSelection[] {
  const { difficulty = 1, startPackId = 1001 } = opts
  return Array.from({ length: count }, (_, i) => ({
    themePackId: ThemePackIdSchema.parse(String(startPackId + i)),
    difficulty,
    giftIds: [],
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
    for (const floor of floorSelections.slice(10, 15)) {
      floor.difficulty = 3 // EXTREME
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
    expect(errors.some((e) => e.code === 'EQUIPMENT_MISSING_SINNER')).toBe(true)
  })

  it('sinner without identity returns EQUIPMENT_MISSING_IDENTITY', () => {
    const equipment = makeValidEquipment()
    // Warning: deliberately invalid input — null identity exercises the missing-identity guard.
    equipment['01'] = { identity: null, egos: { ZAYIN: { id: 'z' } } } as unknown as SinnerEquipment
    const errors = validateEquipment(equipment)
    expect(errors.some((e) => e.code === 'EQUIPMENT_MISSING_IDENTITY')).toBe(true)
  })

  it('sinner without ZAYIN returns EQUIPMENT_MISSING_ZAYIN', () => {
    const equipment = makeValidEquipment()
    // Warning: deliberately invalid input — empty egos exercises the missing-ZAYIN guard.
    equipment['01'] = { identity: { id: 'i' }, egos: {} } as unknown as SinnerEquipment
    const errors = validateEquipment(equipment)
    expect(errors.some((e) => e.code === 'EQUIPMENT_MISSING_ZAYIN')).toBe(true)
  })

  it.each(['abc', '', '1.5', ' 2'])('ignores the non-integer sinner key %j', (key) => {
    const equipment = makeValidEquipment()
    equipment[key] = { identity: { id: 'i' }, egos: { ZAYIN: { id: 'z' } } } as SinnerEquipment
    expect(validateEquipment(equipment)).toHaveLength(0)
  })
})

// ============================================================================
// Floor rules through the validators (floorRules module, BE codes)
// ============================================================================

describe('floor rules through the validators', () => {
  function withPacks(packs: (string | null)[]): MDPlannerContent {
    const content = makeValidContent('5F')
    content.floorSelections = packs.map((themePackId) => ({
      themePackId: themePackId === null ? null : ThemePackIdSchema.parse(themePackId),
      difficulty: 1,
      giftIds: [],
    }))
    return content
  }

  it('all packs present and unique returns no errors', () => {
    const content = withPacks(['1001', '1002', '1003', '1004', '1005'])
    expect(validatePlannerForPublish('My Plan', content, '5F').errors).toEqual([])
  })

  // corpus: scn-publish-floor2-absent
  it('missing pack on floor 3 at publish reports FLOOR_MISSING_THEME_PACK at the floor path', () => {
    const content = withPacks(['1001', '1002', null, '1004', '1005'])
    expect(codesAndFields(validatePlannerForPublish('My Plan', content, '5F'))).toEqual([
      ['FLOOR_MISSING_THEME_PACK', 'floorSelections[2]'],
      ['INVALID_SEQUENCE', 'floorSelections[3]'],
    ])
  })

  // corpus: scn-repeat-floor0-floor3
  it('a repeated pack reports FLOOR_DUPLICATE_THEME_PACK on the later floor at draft', () => {
    const content = withPacks(['1001', '1002', '1003', '1001', '1005'])
    expect(codesAndFields(validatePlannerForDraftSave(content, '5F'))).toEqual([
      ['FLOOR_DUPLICATE_THEME_PACK', 'floorSelections[3].themePackId'],
    ])
  })

  // corpus: floor-sequence-gap
  it('a pack after an empty floor reports INVALID_SEQUENCE at draft', () => {
    const content = withPacks([null, '1002'])
    expect(codesAndFields(validatePlannerForDraftSave(content, '5F'))).toEqual([
      ['INVALID_SEQUENCE', 'floorSelections[1]'],
    ])
  })

  // corpus: bi5-duplicate-gifts-without-pack (Behavior Inventory 5)
  it('duplicate gifts on a pack-less floor report DUPLICATE_VALUE at draft', () => {
    const content = withPacks([null])
    floorAt(content, 0).giftIds = [ENCODED_9001, ENCODED_9001]
    expect(codesAndFields(validatePlannerForDraftSave(content, '5F'))).toEqual([
      ['DUPLICATE_VALUE', 'floorSelections[0].giftIds'],
    ])
  })

  // corpus: silence-floor-element-not-object (Behavior Inventory 3)
  it('a non-object floor reports INVALID_FIELD_TYPE and nothing else', () => {
    const content = makeValidContent('5F')
    ;(content.floorSelections as unknown[])[0] = 'not a floor'
    expect(codesAndFields(validatePlannerForDraftSave(content, '5F'))).toEqual([
      ['INVALID_FIELD_TYPE', 'floorSelections[0]'],
    ])
  })

  // corpus: floor-theme-pack-empty-accepted-as-draft (Behavior Inventory 4)
  it('an empty-string pack is absent: draft passes, publish reports it missing', () => {
    const content = makeValidContent('5F')
    floorAt(content, 4).themePackId = '' as unknown as ThemePackId
    expect(validatePlannerForDraftSave(content, '5F').errors).toEqual([])
    expect(codesAndFields(validatePlannerForPublish('My Plan', content, '5F'))).toEqual([
      ['FLOOR_MISSING_THEME_PACK', 'floorSelections[4]'],
    ])
  })

  // corpus: scn-three-violations-three-floors (Behavior Inventory 11)
  it('draft save returns every violation, one per floor', () => {
    const spec: Record<string, EGOGiftSpec> = { '9002': makeGiftSpec([]) }
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [asEncodedGiftId('9002'), asEncodedGiftId('9002')]
    floorAt(content, 1).giftIds = [ENCODED_9999]
    floorAt(content, 2).themePackId = floorAt(content, 0).themePackId

    expect(codesAndFields(validatePlannerForDraftSave(content, '5F', spec))).toEqual([
      ['DUPLICATE_VALUE', 'floorSelections[0].giftIds'],
      ['FLOOR_DUPLICATE_THEME_PACK', 'floorSelections[2].themePackId'],
      ['FLOOR_UNKNOWN_GIFT_ID', 'floorSelections[1].giftIds'],
    ])
  })

  // corpus: scn-15-stored-on-5f (ADR 136)
  it('floors past the category count are not validated', () => {
    const content = makeValidContent('5F')
    content.floorSelections.push({
      themePackId: floorAt(content, 0).themePackId,
      difficulty: 0,
      giftIds: [ENCODED_9001, ENCODED_9001],
    })
    expect(validatePlannerForPublish('My Plan', content, '5F').errors).toEqual([])
  })
})

// ============================================================================
// validatePlannerForPublish (strict mode)
// ============================================================================

describe('validatePlannerForPublish (strict)', () => {
  it('valid publish-ready 5F content returns isValid: true', () => {
    const { isValid, errors } = validatePlannerForPublish('My Plan', makeValidContent('5F'), '5F')
    expect(isValid).toBe(true)
    expect(errors).toHaveLength(0)
  })

  it('empty title returns MISSING_TITLE error', () => {
    const { isValid, errors } = validatePlannerForPublish('', makeValidContent('5F'), '5F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'MISSING_TITLE')).toBe(true)
  })

  it('whitespace-only title returns MISSING_TITLE error', () => {
    const { isValid, errors } = validatePlannerForPublish('   ', makeValidContent('5F'), '5F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'MISSING_TITLE')).toBe(true)
  })

  it('missing theme pack on active floor returns FLOOR_MISSING_THEME_PACK', () => {
    const content = makeValidContent('5F')
    floorAt(content, 2).themePackId = null
    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'FLOOR_MISSING_THEME_PACK')).toBe(true)
  })

  // corpus: floor-difficulty-hard-required-on-10f
  it('Normal difficulty on 10F floor returns VALUE_OUT_OF_RANGE', () => {
    const content = makeValidContent('10F')
    floorAt(content, 0).difficulty = 0 // NORMAL — invalid for 10F
    const result = validatePlannerForPublish('My Plan', content, '10F')
    expect(result.isValid).toBe(false)
    expect(codesAndFields(result)).toEqual([
      ['VALUE_OUT_OF_RANGE', 'floorSelections[0].difficulty'],
    ])
  })

  // corpus: boundary-seq-normal-after-hard (Behavior Inventory 7)
  it('Normal after Hard on 5F returns INVALID_SEQUENCE at the difficulty', () => {
    const content = makeValidContent('5F')
    floorAt(content, 1).difficulty = 0
    const result = validatePlannerForPublish('My Plan', content, '5F')
    expect(codesAndFields(result)).toEqual([['INVALID_SEQUENCE', 'floorSelections[1].difficulty']])
    expect(toUserFriendlyError(at(result.errors, 0))).toEqual({
      key: 'pages.plannerMD.publish.normalAfterHard',
    })
  })

  // corpus: scn-seq-normal-normal-hard
  it('Hard after Normal on 5F passes', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).difficulty = 0
    floorAt(content, 1).difficulty = 0
    expect(validatePlannerForPublish('My Plan', content, '5F').errors).toEqual([])
  })

  // corpus: scn-10f-difficulty-absent (Behavior Inventory 6)
  it('an absent difficulty at publish returns VALUE_OUT_OF_RANGE', () => {
    const content = makeValidContent('10F')
    delete (floorAt(content, 0) as { difficulty?: DungeonIdx }).difficulty
    expect(codesAndFields(validatePlannerForPublish('My Plan', content, '10F'))).toEqual([
      ['VALUE_OUT_OF_RANGE', 'floorSelections[0].difficulty'],
    ])
    expect(validatePlannerForDraftSave(content, '10F').errors).toEqual([])
  })

  it('renamed keyword is accepted via migration and does not mutate caller state', () => {
    const content = makeValidContent('5F')
    content.selectedKeywords = ['AccelBullet'] // legacy id with a rename target
    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F')
    expect(isValid).toBe(true)
    expect(errors).toHaveLength(0)
    expect(content.selectedKeywords).toEqual(['AccelBullet']) // validator does not mutate input
  })

  it('unknown (non-rename) keyword fails loudly with KEYWORD_INVALID', () => {
    const content = makeValidContent('5F')
    content.selectedKeywords = ['9828', 'GhostKeyword'] // no rename target — genuine corruption
    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'KEYWORD_INVALID')).toBe(true)
  })
})

// ============================================================================
// validateSelectedKeywords (strict membership tier)
// ============================================================================

describe('validateSelectedKeywords', () => {
  it('accepts current keyword ids', () => {
    expect(validateSelectedKeywords(['9828', 'Combustion'])).toHaveLength(0)
  })

  it('rejects a legacy (renamed) keyword id — the read tier should have remapped it', () => {
    const errors = validateSelectedKeywords(['AccelBullet'])
    expect(errors).toHaveLength(1)
    expect(at(errors, 0).code).toBe('KEYWORD_INVALID')
  })

  it('rejects an unknown keyword id', () => {
    const errors = validateSelectedKeywords(['GhostKeyword'])
    expect(errors).toHaveLength(1)
    expect(at(errors, 0).code).toBe('KEYWORD_INVALID')
  })
})

// ============================================================================
// validatePlannerForDraftSave (non-strict mode)
// ============================================================================

describe('validatePlannerForDraftSave (non-strict)', () => {
  it('valid sync-ready content with empty title and no theme packs returns null', () => {
    const content = makeValidContent('5F')
    for (const floor of content.floorSelections) floor.themePackId = null
    // Title is not part of MDPlannerContent — non-strict never checks it
    expect(validatePlannerForDraftSave(content, '5F').errors).toEqual([])
  })

  it('missing theme pack on last floor is allowed (returns null)', () => {
    const content = makeValidContent('5F')
    // Null the last floor only — no subsequent floor can trigger a prerequisite violation
    floorAt(content, 4).themePackId = null
    expect(validatePlannerForDraftSave(content, '5F').errors).toEqual([])
  })

  // corpus: scn-publish-floor2-absent (draft column); contract: INVALID_SEQUENCE keeps the prerequisite key
  it('floor 3 has pack but floor 2 missing still returns prerequisite error', () => {
    const content = makeValidContent('5F')
    floorAt(content, 1).themePackId = null
    const result = validatePlannerForDraftSave(content, '5F')
    expect(codesAndFields(result)).toEqual([['INVALID_SEQUENCE', 'floorSelections[2]']])
    expect(toUserFriendlyError(at(result.errors, 0))).toEqual({
      key: 'pages.plannerMD.previousFloorNoThemePack',
    })
  })

  it('missing equipment sinner returns corruptedState i18n key', () => {
    const content = makeValidContent('5F')
    delete (content.equipment as Record<string, unknown>)['01']
    const result = validatePlannerForDraftSave(content, '5F')
    expect(toUserFriendlyError(at(result.errors, 0)).key).toBe(
      'pages.plannerMD.validation.corruptedState',
    )
  })

  it('unaffordable gift on floor with theme pack returns themePackEgoGiftInconsistency i18n key', () => {
    const content = makeValidContent('5F')
    // Floor 1 has themePackId '1001', add gift that's only for '1024'
    floorAt(content, 0).giftIds = [ENCODED_9220]
    const spec: Record<string, EGOGiftSpec> = {
      '9220': makeGiftSpec(['1024']),
    }
    const result = validatePlannerForDraftSave(content, '5F', spec)
    expect(result.errors.map(toUserFriendlyError)).toEqual([
      {
        key: 'pages.plannerMD.publish.themePackEgoGiftInconsistency',
        params: { pack: '1001', gifts: '9220' },
      },
    ])
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
    expect(errors.some((e) => e.code === 'DEPLOYMENT_INVALID_INDEX')).toBe(true)
  })

  it('index 12 (one beyond max) returns DEPLOYMENT_INVALID_INDEX', () => {
    const errors = validateDeploymentOrder([12])
    expect(errors.some((e) => e.code === 'DEPLOYMENT_INVALID_INDEX')).toBe(true)
  })

  it('multiple invalid indices produce one error each', () => {
    const errors = validateDeploymentOrder([-1, 12, 99])
    expect(errors.filter((e) => e.code === 'DEPLOYMENT_INVALID_INDEX')).toHaveLength(3)
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
    expect(errors.some((e) => e.code === 'SKILL_EA_MISSING_SINNER')).toBe(true)
    const err = errors.find((e) => e.code === 'SKILL_EA_MISSING_SINNER')
    expect((err!.context!.missingSinners as string[]).includes('01')).toBe(true)
  })

  it('invalid slot key "3" returns SKILL_EA_INVALID_SLOT', () => {
    const state = makeValidSkillEAState()
    // Warning: deliberately invalid input — slot key 3 is out of the valid 0-2 range.
    state['01'] = { 0: 3, 1: 2, 3: 1 } as unknown as SkillEAState
    const errors = validateSkillEAState(state)
    expect(errors.some((e) => e.code === 'SKILL_EA_INVALID_SLOT')).toBe(true)
  })

  it('a stored string value is reported as a bad total, not concatenated into one', () => {
    const state = makeValidSkillEAState()
    // Warning: deliberately invalid input — a value that survived storage as text.
    state['01'] = { 0: '3', 1: 2, 2: 1 } as unknown as SkillEAState
    const errors = validateSkillEAState(state)
    const err = errors.find((e) => e.code === 'SKILL_EA_INVALID_TOTAL')
    expect(err).toBeDefined()
    // The string slot is skipped, so the total is the two numeric slots.
    expect(err!.context!.total).toBe(3)
  })

  it('skill EA totalling 7 instead of 6 returns SKILL_EA_INVALID_TOTAL', () => {
    const state = makeValidSkillEAState()
    state['01'] = { 0: 4, 1: 2, 2: 1 } // 4+2+1=7
    const errors = validateSkillEAState(state)
    expect(errors.some((e) => e.code === 'SKILL_EA_INVALID_TOTAL')).toBe(true)
  })

  it.each(['abc', '', '1.5', ' 2'])('ignores the non-integer sinner key %j', (key) => {
    const state = makeValidSkillEAState()
    state[key] = { 0: 3, 1: 2, 2: 1 }
    expect(validateSkillEAState(state)).toHaveLength(0)
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
    expect(errors.some((e) => e.code === 'GIFT_DUPLICATE_ID')).toBe(true)
    expect(at(errors, 0).field).toContain('comprehensiveGiftIds')
  })

  it('unknown gift ID returns GIFT_UNKNOWN_ID when egoGiftSpec is provided', () => {
    const spec: Record<string, EGOGiftSpec> = {
      '9001': makeGiftSpec([]),
    }
    const errors = validateGiftIdArray(['9001', '9999'], 'selectedGiftIds', spec)
    expect(errors).toHaveLength(1)
    expect(at(errors, 0).code).toBe('GIFT_UNKNOWN_ID')
    expect(at(errors, 0).context?.giftId).toBe('9999')
  })

  it.each(['39001', '', '900', '900a', 'gift1', ' 9001'])(
    'malformed gift ID %j returns GIFT_UNKNOWN_ID when egoGiftSpec is provided',
    (giftId) => {
      const spec: Record<string, EGOGiftSpec> = { '9001': makeGiftSpec([]) }
      const errors = validateGiftIdArray([giftId], 'selectedGiftIds', spec)
      expect(errors).toHaveLength(1)
      expect(at(errors, 0).code).toBe('GIFT_UNKNOWN_ID')
    },
  )

  it('all valid gift IDs return no errors when egoGiftSpec is provided', () => {
    const spec: Record<string, EGOGiftSpec> = {
      '9001': makeGiftSpec([]),
      '9002': makeGiftSpec([]),
    }
    expect(validateGiftIdArray(['9001', '9002'], 'selectedGiftIds', spec)).toHaveLength(0)
  })

  it('enhanced gift ID resolves to base ID for existence check', () => {
    const spec: Record<string, EGOGiftSpec> = {
      '9220': makeGiftSpec([]),
    }
    expect(validateGiftIdArray(['19220'], 'selectedGiftIds', spec)).toHaveLength(0)
  })

  it('duplicate is reported before unknown for the same ID', () => {
    const spec: Record<string, EGOGiftSpec> = {}
    const errors = validateGiftIdArray(['9999', '9999'], 'selectedGiftIds', spec)
    expect(errors).toHaveLength(2)
    expect(at(errors, 0).code).toBe('GIFT_UNKNOWN_ID')
    expect(at(errors, 1).code).toBe('GIFT_DUPLICATE_ID')
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
    expect(errors.some((e) => e.code === 'BUFF_EXCEEDS_MAX')).toBe(true)
  })

  it('buff ID 110 (base 10 > max 9) returns BUFF_INVALID_FORMAT', () => {
    const errors = validateStartBuffIds([110]) // 110 % 100 = 10
    expect(errors.some((e) => e.code === 'BUFF_INVALID_FORMAT')).toBe(true)
  })

  it('100 and 200 share base ID 0 and return BUFF_DUPLICATE_BASE_ID', () => {
    const errors = validateStartBuffIds([100, 200]) // both: id % 100 = 0
    expect(errors.some((e) => e.code === 'BUFF_DUPLICATE_BASE_ID')).toBe(true)
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
    expect(errors.some((e) => e.code === 'START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS')).toBe(true)
  })

  it('keyword with duplicate gift ID returns START_GIFT_DUPLICATE_ID', () => {
    const errors = validateStartGiftSelection('someKeyword', ['9001', '9001'])
    expect(errors.some((e) => e.code === 'START_GIFT_DUPLICATE_ID')).toBe(true)
  })
})

// ============================================================================
// validatePlannerForPublish – gift affordability (GIFT_NOT_AFFORDABLE)
// ============================================================================

describe('validatePlannerForPublish – gift affordability', () => {
  const spec: Record<string, EGOGiftSpec> = {
    '9220': makeGiftSpec(['1024']), // only available in pack 1024
    '9001': makeGiftSpec([]), // universal
  }
  const i18n: Record<string, string> = { '9220': 'Dream-Eating Tapir' }

  // corpus: floor-gift-not-affordable
  it("gift '9220' is not affordable for theme pack '1110' → GIFT_NOT_AFFORDABLE at the gift", () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1110')
    floorAt(content, 0).giftIds = [ENCODED_9220]

    const result = validatePlannerForPublish('My Plan', content, '5F', spec)
    expect(result.isValid).toBe(false)
    expect(codesAndFields(result)).toEqual([
      ['GIFT_NOT_AFFORDABLE', 'floorSelections[0].giftIds[0]'],
    ])
    expect(at(onlyCode(result, 'GIFT_NOT_AFFORDABLE'), 0).context.themePackId).toBe('1110')
  })

  it("enhanced gift '19220' (level 1) not affordable for theme pack '1110' → GIFT_NOT_AFFORDABLE", () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1110')
    floorAt(content, 0).giftIds = [ENCODED_19220] // encoded: enhancement=1, base=9220

    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F', spec)
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'GIFT_NOT_AFFORDABLE')).toBe(true)
  })

  it("gift '9220' on its correct pack '1024' passes affordability", () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1024')
    floorAt(content, 0).giftIds = [ENCODED_9220]

    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F', spec)
    expect(isValid).toBe(true)
    expect(errors.filter((e) => e.code === 'GIFT_NOT_AFFORDABLE')).toHaveLength(0)
  })

  it('universal gift (empty themePack) passes affordability on any pack', () => {
    const content = makeValidContent('5F')
    floorAt(content, 2).giftIds = [ENCODED_9001] // pack '1003', universal gift

    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F', spec)
    expect(isValid).toBe(true)
    expect(errors.filter((e) => e.code === 'GIFT_NOT_AFFORDABLE')).toHaveLength(0)
  })

  // contract: GIFT_NOT_AFFORDABLE at floorSelections[i].giftIds[j], one per gift
  it('multiple unaffordable gifts on one floor produce one error per gift', () => {
    const twoGiftSpec: Record<string, EGOGiftSpec> = {
      '9220': makeGiftSpec(['1024']),
      '9221': makeGiftSpec(['1024']),
    }
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1110')
    floorAt(content, 0).giftIds = [ENCODED_9220, ENCODED_9221]

    const result = validatePlannerForPublish('My Plan', content, '5F', twoGiftSpec)
    expect(codesAndFields(result)).toEqual([
      ['GIFT_NOT_AFFORDABLE', 'floorSelections[0].giftIds[0]'],
      ['GIFT_NOT_AFFORDABLE', 'floorSelections[0].giftIds[1]'],
    ])
  })

  it('unaffordable gifts on two separate floors produce one error per floor', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1110') // floor 1
    floorAt(content, 0).giftIds = [ENCODED_9220]
    floorAt(content, 1).themePackId = ThemePackIdSchema.parse('2000') // floor 2 (unique, not '1110')
    floorAt(content, 1).giftIds = [ENCODED_9220]

    const result = validatePlannerForPublish('My Plan', content, '5F', spec)
    expect(onlyCode(result, 'GIFT_NOT_AFFORDABLE').map((e) => e.field)).toEqual([
      'floorSelections[0].giftIds[0]',
      'floorSelections[1].giftIds[0]',
    ])
  })

  it('egoGiftI18n resolves gift ID to display name in error context', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1110')
    floorAt(content, 0).giftIds = [ENCODED_9220]

    const result = validatePlannerForPublish('My Plan', content, '5F', spec, i18n)
    const err: GiftNotAffordableValidationError = at(onlyCode(result, 'GIFT_NOT_AFFORDABLE'), 0)
    expect(err.context.giftName).toBe('Dream-Eating Tapir')
  })

  it('affordability check is skipped when egoGiftSpec is not provided', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [ENCODED_9220] // would fail if spec were provided

    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F') // no spec
    expect(isValid).toBe(true)
    expect(errors.filter((e) => e.code === 'GIFT_NOT_AFFORDABLE')).toHaveLength(0)
  })
})

// ============================================================================
// validatePlannerForPublish – gift existence (FLOOR_UNKNOWN_GIFT_ID)
// ============================================================================

describe('validatePlannerForPublish – gift existence', () => {
  const spec: Record<string, EGOGiftSpec> = {
    '9001': makeGiftSpec([]),
    '9220': makeGiftSpec(['1024']),
  }

  it('unknown floor gift ID returns FLOOR_UNKNOWN_GIFT_ID', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [ENCODED_9999]

    const result = validatePlannerForPublish('My Plan', content, '5F', spec)
    expect(result.isValid).toBe(false)
    const err: FloorUnknownGiftValidationError = at(onlyCode(result, 'FLOOR_UNKNOWN_GIFT_ID'), 0)
    expect(err.floorNumber).toBe(1)
    expect(err.context.giftId).toBe('9999')
  })

  // corpus: floor-gift-unknown (path floorSelections[i].giftIds); contract: one per gift
  it('multiple unknown IDs on one floor produce one FLOOR_UNKNOWN_GIFT_ID error per gift', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [ENCODED_9999, ENCODED_9998]

    const result = validatePlannerForPublish('My Plan', content, '5F', spec)
    expect(
      onlyCode(result, 'FLOOR_UNKNOWN_GIFT_ID').map((e) => [e.field, e.context.giftId]),
    ).toEqual([
      ['floorSelections[0].giftIds', '9999'],
      ['floorSelections[0].giftIds', '9998'],
    ])
  })

  it('unknown IDs on two separate floors produce one FLOOR_UNKNOWN_GIFT_ID error per floor', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [ENCODED_9999]
    floorAt(content, 1).giftIds = [ENCODED_9998]

    const result = validatePlannerForPublish('My Plan', content, '5F', spec)
    expect(onlyCode(result, 'FLOOR_UNKNOWN_GIFT_ID').map((e) => e.floorNumber)).toEqual([1, 2])
  })

  it('valid floor gift IDs return no FLOOR_UNKNOWN_GIFT_ID errors', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [ENCODED_9001, ENCODED_9220]

    const { errors } = validatePlannerForPublish('My Plan', content, '5F', spec)
    expect(errors.filter((e) => e.code === 'FLOOR_UNKNOWN_GIFT_ID')).toHaveLength(0)
  })

  it('existence check is skipped when egoGiftSpec is not provided', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [ENCODED_9999]

    const { isValid } = validatePlannerForPublish('My Plan', content, '5F')
    expect(isValid).toBe(true)
  })

  it('unknown gift in top-level selectedGiftIds returns GIFT_UNKNOWN_ID', () => {
    const content = makeValidContent('5F')
    content.selectedGiftKeyword = 'someKeyword'
    content.selectedGiftIds = [ENCODED_9999]

    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F', spec)
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'GIFT_UNKNOWN_ID')).toBe(true)
  })
})

// ============================================================================
// validatePlannerForPublish – individual validator propagation
// ============================================================================

describe('validatePlannerForPublish – validator propagation', () => {
  it('invalid deployment index propagates DEPLOYMENT_INVALID_INDEX', () => {
    const content = makeValidContent('5F')
    content.deploymentOrder = [-1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]
    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'DEPLOYMENT_INVALID_INDEX')).toBe(true)
  })

  it('invalid skill EA total propagates SKILL_EA_INVALID_TOTAL', () => {
    const content = makeValidContent('5F')
    content.skillEAState['01'] = { 0: 4, 1: 2, 2: 1 } // 4+2+1=7 ≠ 6
    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'SKILL_EA_INVALID_TOTAL')).toBe(true)
  })

  it('duplicate gift in selectedGiftIds propagates GIFT_DUPLICATE_ID', () => {
    const content = makeValidContent('5F')
    content.selectedGiftKeyword = 'someKeyword'
    content.selectedGiftIds = [ENCODED_9001, ENCODED_9001]
    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'GIFT_DUPLICATE_ID')).toBe(true)
  })

  it('11 start buffs propagate BUFF_EXCEEDS_MAX', () => {
    const content = makeValidContent('5F')
    content.selectedBuffIds = [100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 100]
    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'BUFF_EXCEEDS_MAX')).toBe(true)
  })

  it('gift IDs with no keyword propagate START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS', () => {
    const content = makeValidContent('5F')
    content.selectedGiftKeyword = null
    content.selectedGiftIds = [ENCODED_9001]
    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS')).toBe(true)
  })

  it('missing equipment sinner propagates EQUIPMENT_MISSING_SINNER', () => {
    const content = makeValidContent('5F')
    delete (content.equipment as Record<string, unknown>)['01']
    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '5F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'EQUIPMENT_MISSING_SINNER')).toBe(true)
  })
})

// ============================================================================
// validatePlannerForPublish – 15F difficulty rules
// ============================================================================

describe('validatePlannerForPublish – 15F difficulty', () => {
  it('valid 15F (Hard floors 1-10, Extreme floors 11-15) returns isValid: true', () => {
    const { isValid } = validatePlannerForPublish('My Plan', makeValidContent('15F'), '15F')
    expect(isValid).toBe(true)
  })

  // corpus: floor-difficulty-extreme-required-on-15f
  it('floor 11 Hard (not Extreme) in 15F returns VALUE_OUT_OF_RANGE for floor 11', () => {
    const content = makeValidContent('15F')
    floorAt(content, 10).difficulty = 1 // index 10 = floor 11, must be Extreme (3)
    const result = validatePlannerForPublish('My Plan', content, '15F')
    expect(result.isValid).toBe(false)
    expect(codesAndFields(result)).toEqual([
      ['VALUE_OUT_OF_RANGE', 'floorSelections[10].difficulty'],
    ])
    expect(toUserFriendlyError(at(result.errors, 0))).toEqual({
      key: 'pages.plannerMD.publish.requiresExtremeMode',
    })
  })

  // corpus: boundary-seq-10f-normal-in-first-five
  it('floor 1 Normal (not Hard) in 15F returns VALUE_OUT_OF_RANGE', () => {
    const content = makeValidContent('15F')
    floorAt(content, 0).difficulty = 0 // Normal — floors 1-10 must be Hard
    const { isValid, errors } = validatePlannerForPublish('My Plan', content, '15F')
    expect(isValid).toBe(false)
    expect(errors.some((e) => e.code === 'VALUE_OUT_OF_RANGE')).toBe(true)
  })
})

// ============================================================================
// validatePlannerForDraftSave – additional cases
// ============================================================================

describe('validatePlannerForDraftSave – additional cases', () => {
  const spec: Record<string, EGOGiftSpec> = {
    '9220': makeGiftSpec(['1024']),
  }
  const i18n: Record<string, string> = { '9220': 'Dream-Eating Tapir' }

  it('egoGiftI18n name appears in params.gifts for unaffordable gift', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1110')
    floorAt(content, 0).giftIds = [ENCODED_9220]

    const result = validatePlannerForDraftSave(content, '5F', spec, i18n)
    expect(result.errors.map(toUserFriendlyError)).toEqual([
      {
        key: 'pages.plannerMD.publish.themePackEgoGiftInconsistency',
        params: { pack: '1110', gifts: 'Dream-Eating Tapir' },
      },
    ])
  })

  it('affordability check skipped without egoGiftSpec returns no errors', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [ENCODED_9220] // would fail with spec
    expect(validatePlannerForDraftSave(content, '5F').errors).toEqual([])
  })

  it('duplicate pack in non-strict mode returns corruptedState key', () => {
    const content = makeValidContent('5F')
    floorAt(content, 1).themePackId = floorAt(content, 0).themePackId // duplicate
    const result = validatePlannerForDraftSave(content, '5F')
    expect(result.errors.map(toUserFriendlyError)).toEqual([
      { key: 'pages.plannerMD.validation.corruptedState' },
    ])
  })

  it('unknown floor gift ID returns unknownGiftId i18n key', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [ENCODED_9999]

    const result = validatePlannerForDraftSave(content, '5F', spec)
    expect(result.errors.map(toUserFriendlyError)).toEqual([
      { key: 'pages.plannerMD.validation.unknownGiftId', params: { floor: '1', gifts: '9999' } },
    ])
  })

  // Behavior Inventory 11: every violation, existence before affordability
  it('existence and affordability errors are both reported, existence first', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1110')
    floorAt(content, 0).giftIds = [ENCODED_9999, ENCODED_9220]

    const result = validatePlannerForDraftSave(content, '5F', spec)
    expect(codesAndFields(result)).toEqual([
      ['FLOOR_UNKNOWN_GIFT_ID', 'floorSelections[0].giftIds'],
      ['GIFT_NOT_AFFORDABLE', 'floorSelections[0].giftIds[1]'],
    ])
  })
})

// ============================================================================
// validatePlannerForDraftSave – gift affordability (GIFT_NOT_AFFORDABLE)
// ============================================================================

describe('validatePlannerForDraftSave – gift affordability', () => {
  const spec: Record<string, EGOGiftSpec> = {
    '9220': makeGiftSpec(['1024']),
    '9221': makeGiftSpec(['1024']),
    '9001': makeGiftSpec([]),
  }
  const i18n: Record<string, string> = { '9220': 'Dream-Eating Tapir', '9221': 'Pulsating Husk' }

  it('unaffordable gift returns themePackEgoGiftInconsistency with pack and gift name', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1110')
    floorAt(content, 0).giftIds = [ENCODED_9220]

    const result = validatePlannerForDraftSave(content, '5F', spec, i18n)
    expect(result.errors.map(toUserFriendlyError)).toEqual([
      {
        key: 'pages.plannerMD.publish.themePackEgoGiftInconsistency',
        params: { pack: '1110', gifts: 'Dream-Eating Tapir' },
      },
    ])
  })

  it('gift on correct pack returns no errors', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1024')
    floorAt(content, 0).giftIds = [ENCODED_9220]

    expect(validatePlannerForDraftSave(content, '5F', spec).errors).toEqual([])
  })

  it('universal gift on any pack returns no errors', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [ENCODED_9001]

    expect(validatePlannerForDraftSave(content, '5F', spec).errors).toEqual([])
  })

  // contract: GIFT_NOT_AFFORDABLE one per gift; FE grouping goes
  it('multiple unaffordable gifts on one floor name each gift in its own error', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).themePackId = ThemePackIdSchema.parse('1110')
    floorAt(content, 0).giftIds = [ENCODED_9220, ENCODED_9221]

    const result = validatePlannerForDraftSave(content, '5F', spec, i18n)
    expect(result.errors.map(toUserFriendlyError).map((e) => e.params?.gifts)).toEqual([
      'Dream-Eating Tapir',
      'Pulsating Husk',
    ])
  })

  it('floor without theme pack skips affordability check', () => {
    const content = makeValidContent('5F')
    for (const floor of content.floorSelections) floor.themePackId = null
    floorAt(content, 0).giftIds = [ENCODED_9220]

    expect(validatePlannerForDraftSave(content, '5F', spec).errors).toEqual([])
  })

  it('affordability check skipped without egoGiftSpec', () => {
    const content = makeValidContent('5F')
    floorAt(content, 0).giftIds = [ENCODED_9220]

    expect(validatePlannerForDraftSave(content, '5F').errors).toEqual([])
  })
})

describe('validateNoteSizes', () => {
  /** Builds a section note whose serialized size scales 1 byte per ASCII char. */
  function noteWithText(text: string) {
    return {
      content: {
        type: 'doc',
        content: [{ type: 'paragraph', content: [{ type: 'text', text }] }],
      },
    }
  }

  const overhead = calculateNoteByteLength(noteWithText(''))

  it('returns null when all notes are within the limit', () => {
    expect(
      validateNoteSizes({ intro: noteWithText('hello'), 'floor-0': noteWithText('') }),
    ).toBeNull()
  })

  it('returns null for an empty notes map', () => {
    expect(validateNoteSizes({})).toBeNull()
  })

  it('accepts a note exactly at the byte limit', () => {
    const atLimit = noteWithText('a'.repeat(MAX_NOTE_BYTES - overhead))
    expect(calculateNoteByteLength(atLimit)).toBe(MAX_NOTE_BYTES)
    expect(validateNoteSizes({ intro: atLimit })).toBeNull()
  })

  it('flags the offending section one byte over the limit', () => {
    const overLimit = noteWithText('a'.repeat(MAX_NOTE_BYTES - overhead + 1))
    const result = validateNoteSizes({ intro: noteWithText('ok'), deckBuilder: overLimit })

    expect(result).toEqual({
      key: 'pages.plannerMD.validation.noteTooLarge',
      params: { section: 'deckBuilder', limit: String(MAX_NOTE_BYTES) },
    })
  })

  it('measures multibyte content as UTF-8 bytes', () => {
    const koreanCharCount = Math.ceil((MAX_NOTE_BYTES - overhead) / 3) + 1
    const result = validateNoteSizes({ 'floor-3': noteWithText('가'.repeat(koreanCharCount)) })

    expect(result?.params?.section).toBe('floor-3')
  })
})

// ============================================================================
// validatePlannerForImport
// ============================================================================

describe('validatePlannerForImport', () => {
  const registry: PlannerIdRegistry = {
    identityIds: new Set(),
    egoIds: new Set(),
    themePackIds: new Set(['1001', '1002', '1003', '1004', '1005']),
    startBuffIds: new Set(),
  }

  function twoViolations(): MDPlannerContent {
    const content = makeValidContent('5F')
    floorAt(content, 1).difficulty = 0
    floorAt(content, 3).themePackId = floorAt(content, 0).themePackId
    return content
  }

  // Behavior Inventory 11: import shows every violation
  it('a published planner reports both floor violations', () => {
    const planner = buildSaveablePlanner({
      metadata: { published: true },
      content: twoViolations(),
    })
    const floorErrors = validatePlannerForImport(planner, {}, registry).errors.filter((e) =>
      e.field?.startsWith('floorSelections'),
    )
    expect(floorErrors.map((e) => [e.code, e.field])).toEqual([
      ['INVALID_SEQUENCE', 'floorSelections[1].difficulty'],
      ['FLOOR_DUPLICATE_THEME_PACK', 'floorSelections[3].themePackId'],
    ])
  })

  it('a draft planner is checked at the draft stage', () => {
    const planner = buildSaveablePlanner({ content: twoViolations() })
    const floorErrors = validatePlannerForImport(planner, {}, registry).errors.filter((e) =>
      e.field?.startsWith('floorSelections'),
    )
    expect(floorErrors.map((e) => [e.code, e.field])).toEqual([
      ['FLOOR_DUPLICATE_THEME_PACK', 'floorSelections[3].themePackId'],
    ])
  })
})

// ============================================================================
// Entity id registry checks (IDENTITY/EGO/THEME_PACK/START_BUFF_UNKNOWN_ID)
// ============================================================================

describe('entity id registry checks', () => {
  function registryFor(content: MDPlannerContent): PlannerIdRegistry {
    const equipment = Object.values(content.equipment)
    return {
      identityIds: new Set(equipment.map((e) => e.identity.id)),
      egoIds: new Set(equipment.flatMap((e) => Object.values(e.egos).map((ego) => ego.id))),
      themePackIds: new Set(content.floorSelections.map((f) => f.themePackId ?? '')),
      startBuffIds: new Set(['100', '101', '200']),
    }
  }

  function contentWithBuffs(): MDPlannerContent {
    return { ...makeValidContent('5F'), selectedBuffIds: [100, 201] }
  }

  const cases = [
    {
      code: 'IDENTITY_UNKNOWN_ID',
      key: 'pages.plannerMD.validation.unknownIdentityId',
      id: '10199',
      corrupt: (c: MDPlannerContent) => {
        c.equipment['01'] = {
          ...c.equipment['01']!,
          identity: { id: asIdentityId('10199'), uptie: 1, level: 1 },
        }
      },
    },
    {
      code: 'EGO_UNKNOWN_ID',
      key: 'pages.plannerMD.validation.unknownEgoId',
      id: '20199',
      corrupt: (c: MDPlannerContent) => {
        c.equipment['01'] = {
          ...c.equipment['01']!,
          egos: { ZAYIN: { id: asEGOId('20199'), threadspin: 1 } },
        }
      },
    },
    {
      code: 'THEME_PACK_UNKNOWN_ID',
      key: 'pages.plannerMD.validation.unknownThemePackId',
      id: '9999',
      corrupt: (c: MDPlannerContent) => {
        floorAt(c, 2).themePackId = ThemePackIdSchema.parse('9999')
      },
    },
    {
      code: 'START_BUFF_UNKNOWN_ID',
      key: 'pages.plannerMD.validation.unknownStartBuffId',
      id: '201',
      corrupt: () => {},
    },
  ] as const

  it.each(cases)(
    'a known planner passes, so $code comes from the corrupted id alone',
    ({ code }) => {
      const content = { ...makeValidContent('5F'), selectedBuffIds: [100] }
      const registry = registryFor(content)

      expect(
        validatePlannerForDraftSave(content, '5F', undefined, undefined, registry).errors,
      ).toEqual([])
      expect(
        validatePlannerForPublish(
          'My Plan',
          content,
          '5F',
          undefined,
          undefined,
          registry,
        ).errors.map((e) => e.code),
      ).not.toContain(code)
    },
  )

  it.each(cases)('draft save is blocked with $code', ({ key, id, corrupt }) => {
    const content = contentWithBuffs()
    const registry = registryFor(makeValidContent('5F'))
    corrupt(content)

    expect(
      validatePlannerForDraftSave(content, '5F', undefined, undefined, registry).errors.map(
        toUserFriendlyError,
      ),
    ).toContainEqual({ key, params: { id } })
  })

  it.each(cases)('publish reports $code with the offending id', ({ code, id, corrupt }) => {
    const content = contentWithBuffs()
    const registry = registryFor(makeValidContent('5F'))
    corrupt(content)

    const { errors } = validatePlannerForPublish(
      'My Plan',
      content,
      '5F',
      undefined,
      undefined,
      registry,
    )
    expect(errors.find((e) => e.code === code)?.context).toEqual({ id })
  })

  it('checks theme packs only on the floors the category plays', () => {
    const content = { ...makeValidContent('5F'), selectedBuffIds: [100] }
    const registry = registryFor(content)
    content.floorSelections.push({
      themePackId: ThemePackIdSchema.parse('9999'),
      difficulty: 1,
      giftIds: [],
    })

    expect(
      validatePlannerForDraftSave(content, '5F', undefined, undefined, registry).errors,
    ).toEqual([])
  })
})

// ============================================================================
// Gift enhancement band (backend GameDataRegistry ^[12]?(9\d{3})$)
// ============================================================================

describe('gift enhancement band', () => {
  const spec: Record<string, EGOGiftSpec> = { '9123': makeGiftSpec([]) }

  it.each(['9123', '19123', '29123'])('%s resolves to gift 9123 in every gift field', (id) => {
    const content = makeValidContent('5F')
    content.observationGiftIds = [id as EncodedGiftId]
    floorAt(content, 0).giftIds = [id as EncodedGiftId]

    expect(validatePlannerForDraftSave(content, '5F', spec).errors).toEqual([])
  })

  it('39123 is outside the band and unknown in every gift field', () => {
    const content = makeValidContent('5F')
    content.observationGiftIds = ['39123' as EncodedGiftId]
    floorAt(content, 0).giftIds = ['39123' as EncodedGiftId]

    const codes = validatePlannerForPublish('My Plan', content, '5F', spec).errors.map(
      (e) => e.code,
    )
    expect(codes).toContain('GIFT_UNKNOWN_ID')
    expect(codes).toContain('FLOOR_UNKNOWN_GIFT_ID')
  })
})

// ============================================================================
// Retired E.G.O after id normalization (backend PlannerIdMigrations)
// ============================================================================

describe('retired E.G.O after id normalization', () => {
  const RETIRED_EGO = '20199'
  const TABLE: IdMigrationTable = { ego: { rename: {}, drop: [RETIRED_EGO] } }

  function normalizedContentWith(egos: SinnerEquipment['egos']): MDPlannerContent {
    const content = { ...makeValidContent('5F'), selectedBuffIds: [100] }
    content.equipment['01'] = { ...content.equipment['01']!, egos }
    return normalizePlannerIds(
      content as unknown as Record<string, unknown>,
      TABLE,
    ) as unknown as MDPlannerContent
  }

  function registry(): PlannerIdRegistry {
    const content = makeValidContent('5F')
    const equipment = Object.values(content.equipment)
    return {
      identityIds: new Set(equipment.map((e) => e.identity.id)),
      egoIds: new Set(equipment.flatMap((e) => Object.values(e.egos).map((ego) => ego.id))),
      themePackIds: new Set(content.floorSelections.map((f) => f.themePackId ?? '')),
      startBuffIds: new Set(['100']),
    }
  }

  it('keeps it in the ZAYIN slot and reports one EGO_UNKNOWN_ID, not a missing ZAYIN', () => {
    const content = normalizedContentWith({ ZAYIN: { id: asEGOId(RETIRED_EGO), threadspin: 1 } })

    expect(content.equipment['01']?.egos.ZAYIN?.id).toBe(RETIRED_EGO)
    const { errors } = validatePlannerForPublish(
      'My Plan',
      content,
      '5F',
      undefined,
      undefined,
      registry(),
    )
    expect(errors.filter((e) => e.code === 'EGO_UNKNOWN_ID')).toHaveLength(1)
    expect(errors.find((e) => e.code === 'EGO_UNKNOWN_ID')?.context).toEqual({ id: RETIRED_EGO })
    expect(errors.map((e) => e.code)).not.toContain('EQUIPMENT_MISSING_ZAYIN')
  })

  it('empties the TETH slot and reports nothing for it', () => {
    const content = normalizedContentWith({
      ZAYIN: { id: asEGOId('20101'), threadspin: 1 },
      TETH: { id: asEGOId(RETIRED_EGO), threadspin: 1 },
    })

    expect(content.equipment['01']?.egos.TETH).toBeUndefined()
    expect(
      validatePlannerForPublish('My Plan', content, '5F', undefined, undefined, registry()).errors,
    ).toEqual([])
  })
})
