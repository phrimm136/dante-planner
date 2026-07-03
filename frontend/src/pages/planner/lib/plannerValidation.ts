/**
 * Planner Validation
 *
 * Validation functions for planner content, covering equipment, deployment,
 * skill EA, gift IDs, start buffs, start gifts, floor theme packs, difficulty,
 * and note sizes. Mirrors backend PlannerContentValidator validation rules.
 */

import { EGO_TYPES, OFFENSIVE_SKILL_SLOTS, FLOOR_COUNTS, DUNGEON_IDX } from '@/shared/gameData'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import { getBaseGiftId } from '@/pages/egoGift'
import { measureDocBytes } from '@/shared/noteEditor'
import { getUnaffordableGiftIds } from './plannerRules'
import { toUserFriendlyError } from './plannerValidationErrors'
import type { JSONContent } from '@tiptap/core'
import type { MDPlannerContent } from '../types/PlannerTypes'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { SinnerEquipment, SkillEAState } from '../types/DeckTypes'
import type { MDCategory } from '@/shared/gameData'
import type { EGOGiftSpec } from '@/pages/egoGift'
import type {
  PlannerValidationError,
  EquipmentValidationError,
  DeploymentValidationError,
  SkillEAValidationError,
  GiftValidationError,
  BuffValidationError,
  StartGiftValidationError,
  FloorValidationError,
  DifficultyValidationError,
} from './plannerValidationErrors'

// ============================================================================
// Constants (Match Backend Validation Rules)
// ============================================================================

/** Equipment keys are 1-indexed (1-12) */
const MIN_EQUIPMENT_SINNER = 1
const MAX_EQUIPMENT_SINNER = 12

/** DeploymentOrder values are 0-indexed (0-11) */
const MIN_DEPLOYMENT_SINNER = 0
const MAX_DEPLOYMENT_SINNER = 11

/** All sinner keys that must be present (2-digit format) */
const ALL_SINNER_KEYS = ['01', '02', '03', '04', '05', '06', '07', '08', '09', '10', '11', '12'] as const

/** Required EGO type for each sinner */
const REQUIRED_EGO_TYPE = 'ZAYIN'

/** Valid skill slots (0=S1, 1=S2, 2=S3) */
const VALID_SKILL_SLOTS = new Set(['0', '1', '2'])

/** Skill EA total must equal 6 (3+2+1 default distribution) */
const SKILL_EA_TOTAL = 6

/** Start buff constraints */
const MAX_START_BUFFS = 10
const MIN_BUFF_BASE_ID = 0
const MAX_BUFF_BASE_ID = 9

// ============================================================================
// Validation Functions
// ============================================================================

/**
 * Validate equipment configuration
 * Rules:
 * - All 12 sinners must be present (keys 1-12 or 01-12)
 * - Each sinner must have an identity with valid ID
 * - Each sinner must have a ZAYIN EGO with valid ID
 * - Max 5 EGO types, all unique, from valid set
 */
export function validateEquipment(equipment: Record<string, SinnerEquipment>): EquipmentValidationError[] {
  const errors: EquipmentValidationError[] = []

  // Collect present sinner keys and normalize to 2-digit format
  const presentKeys = new Set<string>()
  for (const key of Object.keys(equipment)) {
    try {
      const index = parseInt(key, 10)
      if (index < MIN_EQUIPMENT_SINNER || index > MAX_EQUIPMENT_SINNER) {
        continue
      }
      presentKeys.add(String(index).padStart(2, '0'))
    } catch {
      continue
    }
  }

  // Check all 12 sinners are present
  const missingSinners = ALL_SINNER_KEYS.filter(key => !presentKeys.has(key))
  if (missingSinners.length > 0) {
    errors.push({
      code: 'EQUIPMENT_MISSING_SINNER',
      message: `Missing equipment for sinners: ${missingSinners.join(', ')}`,
      field: 'equipment',
      context: { missingSinners },
    })
  }

  // Validate each sinner's equipment
  for (const sinnerKey of presentKeys) {
    // Try both formats (with and without leading zero)
    const sinnerEquipment = equipment[sinnerKey] || equipment[String(parseInt(sinnerKey, 10))]
    if (!sinnerEquipment) continue

    // Check identity exists and has ID
    if (!sinnerEquipment.identity || !sinnerEquipment.identity.id) {
      errors.push({
        code: 'EQUIPMENT_MISSING_IDENTITY',
        message: `Sinner ${sinnerKey} is missing identity`,
        field: `equipment.${sinnerKey}.identity`,
        context: { sinnerKey },
      })
    }

    // Check EGOs exist
    if (!sinnerEquipment.egos) {
      errors.push({
        code: 'EQUIPMENT_MISSING_ZAYIN',
        message: `Sinner ${sinnerKey} is missing EGO configuration`,
        field: `equipment.${sinnerKey}.egos`,
        context: { sinnerKey },
      })
      continue
    }

    // Validate EGO types: max 5, unique, from valid set
    const egoTypes = Object.keys(sinnerEquipment.egos)
    const validEGOTypes = new Set(EGO_TYPES)
    const invalidTypes = egoTypes.filter(type => !validEGOTypes.has(type as typeof EGO_TYPES[number]))

    if (invalidTypes.length > 0) {
      errors.push({
        code: 'EQUIPMENT_INVALID_EGO_TYPES',
        message: `Sinner ${sinnerKey} has invalid EGO types: ${invalidTypes.join(', ')}`,
        field: `equipment.${sinnerKey}.egos`,
        context: { sinnerKey, invalidTypes },
      })
    }

    if (egoTypes.length > EGO_TYPES.length) {
      errors.push({
        code: 'EQUIPMENT_INVALID_EGO_TYPES',
        message: `Sinner ${sinnerKey} has more than ${EGO_TYPES.length} EGO types`,
        field: `equipment.${sinnerKey}.egos`,
        context: { sinnerKey, count: egoTypes.length },
      })
    }

    // Check ZAYIN EGO is required
    if (!sinnerEquipment.egos[REQUIRED_EGO_TYPE]) {
      errors.push({
        code: 'EQUIPMENT_MISSING_ZAYIN',
        message: `Sinner ${sinnerKey} is missing required ${REQUIRED_EGO_TYPE} EGO`,
        field: `equipment.${sinnerKey}.egos.${REQUIRED_EGO_TYPE}`,
        context: { sinnerKey },
      })
    } else if (!sinnerEquipment.egos[REQUIRED_EGO_TYPE]?.id) {
      errors.push({
        code: 'EQUIPMENT_MISSING_ZAYIN',
        message: `Sinner ${sinnerKey} ${REQUIRED_EGO_TYPE} EGO is missing ID`,
        field: `equipment.${sinnerKey}.egos.${REQUIRED_EGO_TYPE}.id`,
        context: { sinnerKey },
      })
    }
  }

  return errors
}

/**
 * Validate deployment order
 * Rules:
 * - All values must be numbers in range 0-11
 */
export function validateDeploymentOrder(deploymentOrder: number[]): DeploymentValidationError[] {
  const errors: DeploymentValidationError[] = []

  for (let i = 0; i < deploymentOrder.length; i++) {
    const index = deploymentOrder[i]
    if (typeof index !== 'number' || index < MIN_DEPLOYMENT_SINNER || index > MAX_DEPLOYMENT_SINNER) {
      errors.push({
        code: 'DEPLOYMENT_INVALID_INDEX',
        message: `Deployment order[${i}] has invalid sinner index: ${index} (must be 0-11)`,
        field: `deploymentOrder[${i}]`,
        context: { index: i, value: index },
      })
    }
  }

  return errors
}

/**
 * Validate skill EA state
 * Rules:
 * - All 12 sinners must be present
 * - Valid skill slot keys (0, 1, 2)
 * - No duplicate slots per sinner
 * - Each sinner's skill slots sum to SKILL_EA_TOTAL (6)
 */
export function validateSkillEAState(skillEAState: Record<string, SkillEAState>): SkillEAValidationError[] {
  const errors: SkillEAValidationError[] = []

  // Collect present sinner keys
  const presentKeys = new Set<string>()
  for (const key of Object.keys(skillEAState)) {
    try {
      const index = parseInt(key, 10)
      if (index < MIN_EQUIPMENT_SINNER || index > MAX_EQUIPMENT_SINNER) {
        continue
      }
      presentKeys.add(String(index).padStart(2, '0'))
    } catch {
      continue
    }
  }

  // Check all 12 sinners are present
  const missingSinners = ALL_SINNER_KEYS.filter(key => !presentKeys.has(key))
  if (missingSinners.length > 0) {
    errors.push({
      code: 'SKILL_EA_MISSING_SINNER',
      message: `Missing skill EA state for sinners: ${missingSinners.join(', ')}`,
      field: 'skillEAState',
      context: { missingSinners },
    })
  }

  // Validate each sinner's skill slots
  for (const sinnerKey of presentKeys) {
    const sinnerSkills = skillEAState[sinnerKey] || skillEAState[String(parseInt(sinnerKey, 10))]
    if (!sinnerSkills) continue

    const seenSlots = new Set<string>()
    let total = 0

    for (const slotKey of Object.keys(sinnerSkills)) {
      // Check valid skill slot key
      if (!VALID_SKILL_SLOTS.has(slotKey)) {
        errors.push({
          code: 'SKILL_EA_INVALID_SLOT',
          message: `Sinner ${sinnerKey} has invalid skill slot: ${slotKey} (must be 0, 1, or 2)`,
          field: `skillEAState.${sinnerKey}.${slotKey}`,
          context: { sinnerKey, slotKey },
        })
        continue
      }

      // Check for duplicates
      if (seenSlots.has(slotKey)) {
        errors.push({
          code: 'SKILL_EA_DUPLICATE_SLOT',
          message: `Sinner ${sinnerKey} has duplicate skill slot: ${slotKey}`,
          field: `skillEAState.${sinnerKey}`,
          context: { sinnerKey, slotKey },
        })
      }
      seenSlots.add(slotKey)

      total += sinnerSkills[slotKey as unknown as typeof OFFENSIVE_SKILL_SLOTS[number]]
    }

    // Check total equals SKILL_EA_TOTAL
    if (total !== SKILL_EA_TOTAL) {
      errors.push({
        code: 'SKILL_EA_INVALID_TOTAL',
        message: `Sinner ${sinnerKey} skill EA total is ${total}, expected ${SKILL_EA_TOTAL}`,
        field: `skillEAState.${sinnerKey}`,
        context: { sinnerKey, total, expected: SKILL_EA_TOTAL },
      })
    }
  }

  return errors
}

/**
 * Validate gift ID array for duplicates and existence
 */
export function validateGiftIdArray(
  giftIds: string[],
  fieldName: string,
  egoGiftSpec?: Record<string, EGOGiftSpec>
): GiftValidationError[] {
  const errors: GiftValidationError[] = []
  const seen = new Set<string>()

  for (let i = 0; i < giftIds.length; i++) {
    const giftId = giftIds[i]
    if (seen.has(giftId)) {
      errors.push({
        code: 'GIFT_DUPLICATE_ID',
        message: `Duplicate gift ID in ${fieldName}: ${giftId}`,
        field: `${fieldName}[${i}]`,
        context: { giftId, index: i },
      })
      continue
    }
    seen.add(giftId)

    if (egoGiftSpec) {
      const baseId = getBaseGiftId(giftId)
      if (!(baseId in egoGiftSpec)) {
        errors.push({
          code: 'GIFT_UNKNOWN_ID',
          message: `Gift ID '${giftId}' not found in ${fieldName}`,
          field: `${fieldName}[${i}]`,
          context: { giftId },
        })
      }
    }
  }

  return errors
}

/**
 * Validate start buff IDs
 * Rules:
 * - Max 10 buffs
 * - ID format: {1|2|3}{00-09} (100-109, 200-209, 300-309)
 * - No duplicate base IDs (can't have both 100 and 200)
 */
export function validateStartBuffIds(buffIds: number[]): BuffValidationError[] {
  const errors: BuffValidationError[] = []

  // Check max count
  if (buffIds.length > MAX_START_BUFFS) {
    errors.push({
      code: 'BUFF_EXCEEDS_MAX',
      message: `Start buffs count ${buffIds.length} exceeds maximum ${MAX_START_BUFFS}`,
      field: 'selectedBuffIds',
      context: { count: buffIds.length, max: MAX_START_BUFFS },
    })
  }

  // Track base IDs to detect duplicates
  const seenBaseIds = new Set<number>()

  for (let i = 0; i < buffIds.length; i++) {
    const buffId = buffIds[i]

    // Extract base ID (00-09 part)
    const baseId = buffId % 100

    if (baseId < MIN_BUFF_BASE_ID || baseId > MAX_BUFF_BASE_ID) {
      errors.push({
        code: 'BUFF_INVALID_FORMAT',
        message: `Start buff ID ${buffId} has invalid base ID ${baseId} (must be 00-09)`,
        field: `selectedBuffIds[${i}]`,
        context: { buffId, baseId, index: i },
      })
      continue
    }

    // Check for duplicate base IDs
    if (seenBaseIds.has(baseId)) {
      errors.push({
        code: 'BUFF_DUPLICATE_BASE_ID',
        message: `Start buffs have duplicate base ID ${baseId} (buff ID ${buffId})`,
        field: `selectedBuffIds[${i}]`,
        context: { buffId, baseId, index: i },
      })
    }
    seenBaseIds.add(baseId)
  }

  return errors
}

/**
 * Validate start gift selection
 * Rules:
 * - If no keyword, selectedGiftIds must be empty
 * - If keyword present, it must be valid (frontend can't check this without game data)
 * - No duplicate gift IDs
 */
export function validateStartGiftSelection(
  selectedGiftKeyword: string | null,
  selectedGiftIds: string[]
): StartGiftValidationError[] {
  const errors: StartGiftValidationError[] = []

  // If no keyword, selectedGiftIds must be empty
  if (!selectedGiftKeyword && selectedGiftIds.length > 0) {
    errors.push({
      code: 'START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS',
      message: `Start gift IDs are selected but no keyword is set`,
      field: 'selectedGiftIds',
      context: { giftCount: selectedGiftIds.length },
    })
  }

  // Check for duplicates
  const seen = new Set<string>()
  for (let i = 0; i < selectedGiftIds.length; i++) {
    const giftId = selectedGiftIds[i]
    if (seen.has(giftId)) {
      errors.push({
        code: 'START_GIFT_DUPLICATE_ID',
        message: `Duplicate start gift ID: ${giftId}`,
        field: `selectedGiftIds[${i}]`,
        context: { giftId, index: i },
      })
    }
    seen.add(giftId)
  }

  return errors
}

/**
 * Validates that all floor theme pack selections meet save requirements
 *
 * Rules enforced:
 * 1. Each floor must have a theme pack selected (no null values)
 * 2. Progressive prerequisite: Floor N can only have a theme pack if floor N-1 has one
 * 3. No duplicate theme pack IDs across floors (each floor must use a different theme pack)
 * 4. No duplicate gift IDs per floor
 *
 * @param floorSelections - Array of floor selections to validate
 * @param floorCount - Number of active floors (5, 10, or 15)
 * @returns Array of validation errors (empty if valid)
 *
 * @example
 * // Valid: All floors have distinct theme packs
 * const floors = [
 *   { themePackId: '1001', difficulty: 0, giftIds: new Set() },
 *   { themePackId: '1002', difficulty: 0, giftIds: new Set() },
 * ]
 * validateFloorThemePacksForSave(floors, 2) // Returns []
 *
 * @example
 * // Invalid: Floors 1 and 2 have duplicate theme pack
 * const floors = [
 *   { themePackId: '1001', difficulty: 0, giftIds: new Set() },
 *   { themePackId: '1001', difficulty: 0, giftIds: new Set() }, // Duplicate!
 * ]
 * validateFloorThemePacksForSave(floors, 2)
 * // Returns [{ code: 'FLOOR_DUPLICATE_THEME_PACK', floorIndex: 1, floorNumber: 2, ... }]
 */
export function validateFloorThemePacksForSave(
  floorSelections: FloorThemeSelection[],
  floorCount: number
): FloorValidationError[] {
  const errors: FloorValidationError[] = []

  // Track seen theme pack IDs to detect duplicates across floors
  const seenThemePackIds = new Map<string, number>() // themePackId -> first floor index

  // Check only the active floors based on category (5F, 10F, 15F)
  for (let i = 0; i < floorCount; i++) {
    const floor = floorSelections[i]
    const floorNumber = i + 1

    // Rule 1: Each floor must have a theme pack
    if (!floor.themePackId) {
      errors.push({
        code: 'FLOOR_MISSING_THEME_PACK',
        message: `Floor ${floorNumber} must have a theme pack selected`,
        field: `floorSelections[${i}].themePackId`,
        floorIndex: i,
        floorNumber,
      })
      continue // Skip other checks if floor is missing theme pack
    }

    // Rule 2: Progressive prerequisite (skip for floor 1)
    if (i > 0) {
      const previousFloor = floorSelections[i - 1]
      if (!previousFloor.themePackId) {
        errors.push({
          code: 'FLOOR_PREREQUISITE_VIOLATION',
          message: `Floor ${floorNumber} cannot have a theme pack because Floor ${i} is missing one`,
          field: `floorSelections[${i}].themePackId`,
          floorIndex: i,
          floorNumber,
          context: { previousFloorIndex: i - 1 },
        })
      }
    }

    // Rule 3: No duplicate theme pack IDs across floors
    const firstFloorWithThisPack = seenThemePackIds.get(floor.themePackId)
    if (firstFloorWithThisPack !== undefined) {
      errors.push({
        code: 'FLOOR_DUPLICATE_THEME_PACK',
        message: `Floor ${floorNumber} has duplicate theme pack '${floor.themePackId}' (already used on Floor ${firstFloorWithThisPack + 1})`,
        field: `floorSelections[${i}].themePackId`,
        floorIndex: i,
        floorNumber,
        context: {
          themePackId: floor.themePackId,
          firstFloorIndex: firstFloorWithThisPack,
          firstFloorNumber: firstFloorWithThisPack + 1,
        },
      })
    } else {
      seenThemePackIds.set(floor.themePackId, i)
    }

    // Rule 4: No duplicate gift IDs within this floor's gifts
    const giftIds = Array.from(floor.giftIds)
    const seenGiftIds = new Set<string>()
    for (let j = 0; j < giftIds.length; j++) {
      const giftId = giftIds[j]
      if (seenGiftIds.has(giftId)) {
        errors.push({
          code: 'FLOOR_DUPLICATE_GIFT_ID',
          message: `Floor ${floorNumber} has duplicate gift ID: ${giftId}`,
          field: `floorSelections[${i}].giftIds[${j}]`,
          floorIndex: i,
          floorNumber,
          context: { giftId },
        })
      }
      seenGiftIds.add(giftId)
    }
  }

  return errors
}

/**
 * Validates floor difficulty requirements based on category
 *
 * Rules:
 * - 5F: All floors must be Normal(0) or Hard(1)
 * - 10F: All floors must be Hard(1)
 * - 15F: Floors 1-10 must be Hard(1), Floors 11-15 must be Extreme(3)
 */
function validateFloorDifficulties(
  floorSelections: FloorThemeSelection[],
  category: MDCategory,
  floorCount: number
): DifficultyValidationError[] {
  const errors: DifficultyValidationError[] = []

  for (let i = 0; i < floorCount; i++) {
    const floor = floorSelections[i]
    if (!floor) continue

    const difficulty = floor.difficulty
    const floorNumber = i + 1

    switch (category) {
      case '5F':
        // 5F: All floors must be Normal(0) or Hard(1)
        if (difficulty !== DUNGEON_IDX.NORMAL && difficulty !== DUNGEON_IDX.HARD) {
          errors.push({
            code: 'DIFFICULTY_INVALID_FOR_CATEGORY',
            message: `Floor ${floorNumber} must be Normal or Hard for 5F category`,
            field: `floorSelections[${i}].difficulty`,
            floorIndex: i,
            floorNumber,
          })
        }
        break
      case '10F':
        // 10F: All floors must be Hard(1)
        if (difficulty !== DUNGEON_IDX.HARD) {
          errors.push({
            code: 'DIFFICULTY_INVALID_FOR_CATEGORY',
            message: `Floor ${floorNumber} must be Hard for 10F category`,
            field: `floorSelections[${i}].difficulty`,
            floorIndex: i,
            floorNumber,
          })
        }
        break
      case '15F':
        // 15F: Floors 1-10 must be Hard(1), Floors 11-15 must be Extreme(3)
        if (i < 10 && difficulty !== DUNGEON_IDX.HARD) {
          errors.push({
            code: 'DIFFICULTY_INVALID_FOR_CATEGORY',
            message: `Floor ${floorNumber} must be Hard for 15F category`,
            field: `floorSelections[${i}].difficulty`,
            floorIndex: i,
            floorNumber,
          })
        }
        if (i >= 10 && difficulty !== DUNGEON_IDX.EXTREME) {
          errors.push({
            code: 'DIFFICULTY_INVALID_FOR_CATEGORY',
            message: `Floor ${floorNumber} must be Extreme for 15F category`,
            field: `floorSelections[${i}].difficulty`,
            floorIndex: i,
            floorNumber,
          })
        }
        break
    }
  }

  return errors
}

/**
 * Validates that all selected ego gift IDs exist in the spec list
 *
 * @param floorSelections - Floor selections to validate
 * @param floorCount - Number of active floors based on category
 * @param egoGiftSpec - EGO Gift spec data keyed by gift ID
 * @returns Array of validation errors (one per floor with unknown gift IDs)
 */
function validateFloorGiftExistence(
  floorSelections: FloorThemeSelection[],
  floorCount: number,
  egoGiftSpec: Record<string, EGOGiftSpec>
): FloorValidationError[] {
  return floorSelections
    .slice(0, floorCount)
    .flatMap((floor, i) => {
      const unknownIds: string[] = []

      for (const giftId of floor.giftIds) {
        const baseId = getBaseGiftId(giftId)
        if (!(baseId in egoGiftSpec)) {
          unknownIds.push(giftId)
        }
      }

      if (unknownIds.length === 0) return []

      const floorNumber = i + 1
      return [{
        code: 'GIFT_UNKNOWN_ID' as const,
        message: `Floor ${floorNumber}: unknown gift ID(s): ${unknownIds.join(', ')}`,
        field: `floorSelections[${i}].giftIds`,
        floorIndex: i,
        floorNumber,
        context: { giftIds: unknownIds },
      }]
    })
}

/**
 * Validates that all selected ego gifts are affordable for their floor's theme pack.
 * Assumes all gift IDs exist in the spec (run validateFloorGiftExistence first).
 */
function validateFloorGiftAffordability(
  floorSelections: FloorThemeSelection[],
  floorCount: number,
  egoGiftSpec: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>
): FloorValidationError[] {
  return floorSelections
    .slice(0, floorCount)
    .flatMap((floor, i) => {
      if (!floor?.themePackId) return []

      const unaffordableIds = getUnaffordableGiftIds(floor.giftIds, floor.themePackId, egoGiftSpec)

      if (unaffordableIds.length === 0) return []

      const floorNumber = i + 1
      const giftNames = unaffordableIds.map(id => egoGiftI18n?.[getBaseGiftId(id)] ?? id).join(', ')

      return [{
        code: 'FLOOR_UNAFFORDABLE_GIFT' as const,
        message: `Floor ${floorNumber} has ${unaffordableIds.length} gift(s) not available for theme pack: ${giftNames}`,
        field: `floorSelections[${i}].giftIds`,
        floorIndex: i,
        floorNumber,
        context: { giftIds: unaffordableIds, giftNames, themePackId: floor.themePackId },
      }]
    })
}

/**
 * Comprehensive planner validation for save operations
 * Runs all validation checks and returns consolidated errors
 *
 * This function mirrors the backend PlannerContentValidator validation logic:
 * - Equipment: All 12 sinners, identity + ZAYIN EGO, valid EGO types
 * - Deployment: Valid sinner indices (0-11)
 * - Skill EA: All 12 sinners, valid slots, correct totals
 * - Gift IDs: No duplicates in selectedGiftIds, observationGiftIds, comprehensiveGiftIds
 * - Start Buffs: Max 10, valid format, no duplicate base IDs
 * - Start Gifts: Keyword/gifts consistency, no duplicates
 * - Floor Selections: Required theme packs, progressive prerequisites, no duplicate theme packs across floors, no duplicate gifts per floor
 *
 * @param content - MD planner content to validate
 * @param category - MD category (5F, 10F, or 15F) to determine active floor count
 * @returns Object with isValid flag and array of all validation errors
 *
 * @example
 * const result = validatePlannerForPublish('My Plan', plannerContent, '5F')
 * if (!result.isValid) {
 *   console.error('Validation failed:', result.errors)
 *   // Show first error to user
 *   toast.error(result.errors[0].message)
 * }
 */
export function validatePlannerForPublish(
  title: string | undefined,
  content: MDPlannerContent,
  category: MDCategory,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>
): { isValid: boolean; errors: PlannerValidationError[] } {
  const errors: PlannerValidationError[] = []

  // 0. Title validation (strict mode — required for publish)
  if (!title || title.trim() === '') {
    errors.push({ code: 'MISSING_TITLE', message: 'Title is required for publishing', field: 'title' })
  }

  // 1. Equipment validation
  errors.push(...validateEquipment(content.equipment))

  // 2. Deployment order validation
  errors.push(...validateDeploymentOrder(content.deploymentOrder))

  // 3. Skill EA state validation
  errors.push(...validateSkillEAState(content.skillEAState))

  // 4. Gift IDs validation (all three arrays)
  errors.push(...validateGiftIdArray(content.selectedGiftIds, 'selectedGiftIds', egoGiftSpec))
  errors.push(...validateGiftIdArray(content.observationGiftIds, 'observationGiftIds', egoGiftSpec))
  errors.push(...validateGiftIdArray(content.comprehensiveGiftIds, 'comprehensiveGiftIds', egoGiftSpec))

  // 5. Start buffs validation
  errors.push(...validateStartBuffIds(content.selectedBuffIds))

  // 6. Start gifts validation
  errors.push(...validateStartGiftSelection(content.selectedGiftKeyword, content.selectedGiftIds))

  // 7. Floor selections validation
  const floorCount = FLOOR_COUNTS[category]
  // Deserialize floor selections (convert giftIds from string[] to Set<string>)
  const deserializedFloorSelections: FloorThemeSelection[] = content.floorSelections.map(floor => ({
    ...floor,
    giftIds: new Set(floor.giftIds)
  }))
  errors.push(...validateFloorThemePacksForSave(deserializedFloorSelections, floorCount))

  // 8. Difficulty validation (full rules based on category)
  errors.push(...validateFloorDifficulties(deserializedFloorSelections, category, floorCount))

  // 9. Gift existence validation (if egoGiftSpec is provided)
  if (egoGiftSpec) {
    errors.push(...validateFloorGiftExistence(deserializedFloorSelections, floorCount, egoGiftSpec))
  }

  // 10. Gift affordability validation (if egoGiftSpec is provided; assumes existence check passed)
  if (egoGiftSpec) {
    errors.push(...validateFloorGiftAffordability(deserializedFloorSelections, floorCount, egoGiftSpec, egoGiftI18n))
  }

  return {
    isValid: errors.length === 0,
    errors,
  }
}

/**
 * Non-strict validation for sync/draft-save operations
 * Runs all structural checks but allows missing title and missing theme packs.
 * Returns the first error as an i18n key+params, or null if valid.
 *
 * Mirrors BE relaxed mode: title and theme packs are optional; all other checks
 * (equipment, deployment, skill EA, gift IDs, start buffs, start gifts,
 * floor prerequisites, gift affordability) run identically to strict mode.
 *
 * @param content - MD planner content to validate
 * @param category - MD category (5F, 10F, or 15F)
 * @param egoGiftSpec - EGO Gift spec data (optional, skips affordability check if not provided)
 * @param egoGiftI18n - EGO Gift i18n names (optional, uses IDs if not provided)
 * @returns Object with i18n key and params, or null if valid
 */
export function validatePlannerForDraftSave(
  content: MDPlannerContent,
  category: MDCategory,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>
): { key: string; params?: Record<string, string> } | null {
  const errors: PlannerValidationError[] = []

  // 1. Equipment validation
  errors.push(...validateEquipment(content.equipment))

  // 2. Deployment order validation
  errors.push(...validateDeploymentOrder(content.deploymentOrder))

  // 3. Skill EA state validation
  errors.push(...validateSkillEAState(content.skillEAState))

  // 4. Gift IDs validation (all three arrays)
  errors.push(...validateGiftIdArray(content.selectedGiftIds, 'selectedGiftIds', egoGiftSpec))
  errors.push(...validateGiftIdArray(content.observationGiftIds, 'observationGiftIds', egoGiftSpec))
  errors.push(...validateGiftIdArray(content.comprehensiveGiftIds, 'comprehensiveGiftIds', egoGiftSpec))

  // 5. Start buffs validation
  errors.push(...validateStartBuffIds(content.selectedBuffIds))

  // 6. Start gifts validation
  errors.push(...validateStartGiftSelection(content.selectedGiftKeyword, content.selectedGiftIds))

  // 7. Floor selections validation (non-strict: theme packs optional)
  const floorCount = FLOOR_COUNTS[category]
  const deserializedFloorSelections: FloorThemeSelection[] = content.floorSelections.map(floor => ({
    ...floor,
    giftIds: new Set(floor.giftIds)
  }))
  const floorErrors = validateFloorThemePacksForSave(deserializedFloorSelections, floorCount)
  // Filter out FLOOR_MISSING_THEME_PACK — theme packs are optional in non-strict mode.
  // Prerequisites and duplicates still fire when a floor *does* have a pack.
  errors.push(...floorErrors.filter(e => e.code !== 'FLOOR_MISSING_THEME_PACK'))

  // 8. Gift existence validation
  if (egoGiftSpec) {
    errors.push(...validateFloorGiftExistence(deserializedFloorSelections, floorCount, egoGiftSpec))
  }

  // 9. Gift affordability (assumes existence check passed; guards on themePackId being set)
  if (egoGiftSpec) {
    errors.push(...validateFloorGiftAffordability(deserializedFloorSelections, floorCount, egoGiftSpec, egoGiftI18n))
  }

  if (errors.length === 0) return null
  return toUserFriendlyError(errors[0])
}

/**
 * Validates that no section note exceeds the byte cap.
 *
 * Returns the first offending section as a user-friendly error so save can be
 * blocked with an actionable message, rather than relying on schema discard
 * (which would silently drop the whole planner). The editor enforces the same
 * cap on input; this is the save-time backstop for legacy oversized notes.
 *
 * @param sectionNotes - serialized section notes keyed by section identifier
 * @returns user-friendly error for the first oversized note, or null if all fit
 */
export function validateNoteSizes(
  sectionNotes: Record<string, { content: unknown }>
): { key: string; params?: Record<string, string> } | null {
  for (const [section, note] of Object.entries(sectionNotes ?? {})) {
    if (measureDocBytes(note.content as JSONContent) > MAX_NOTE_BYTES) {
      return {
        key: 'pages.plannerMD.validation.noteTooLarge',
        params: { section, limit: String(MAX_NOTE_BYTES) },
      }
    }
  }
  return null
}
