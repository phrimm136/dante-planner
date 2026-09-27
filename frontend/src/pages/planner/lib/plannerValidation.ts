import {
  EGO_TYPES,
  OFFENSIVE_SKILL_SLOTS,
  FLOOR_COUNTS,
  ALLOWED_FLOOR_DIFFICULTIES,
  DUNGEON_NAME_BY_IDX,
  PLANNER_KEYWORDS,
  migrateKeywords,
  EncodedGiftIdSchema,
} from '@/shared/gameData'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import { decodeGiftSelection, giftDisplayName, hasGiftId } from '@/pages/egoGift'
import { measureDocBytes } from '@/shared/noteEditor'
import { getUnaffordableGiftIds } from './plannerRules'
import { toUserFriendlyError } from './plannerValidationErrors'
import type { JSONContent } from '@tiptap/core'
import { isMDPlanner } from '../types/PlannerTypes'
import type { MDPlannerContent, SaveablePlanner } from '../types/PlannerTypes'
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
  KeywordValidationError,
  EntityIdValidationError,
} from './plannerValidationErrors'

export interface PlannerIdRegistry {
  identityIds: ReadonlySet<string>
  egoIds: ReadonlySet<string>
  themePackIds: ReadonlySet<string>
  startBuffIds: ReadonlySet<string>
}

/** Equipment keys are 1-indexed (1-12) */
const MIN_EQUIPMENT_SINNER = 1
const MAX_EQUIPMENT_SINNER = 12

/** DeploymentOrder values are 0-indexed (0-11) */
const MIN_DEPLOYMENT_SINNER = 0
const MAX_DEPLOYMENT_SINNER = 11

/** All sinner keys that must be present (2-digit format) */
const ALL_SINNER_KEYS = [
  '01',
  '02',
  '03',
  '04',
  '05',
  '06',
  '07',
  '08',
  '09',
  '10',
  '11',
  '12',
] as const

export const REQUIRED_EGO_TYPE = 'ZAYIN'

/** Valid skill slots (0=S1, 1=S2, 2=S3) */
const VALID_SKILL_SLOTS = new Set(['0', '1', '2'])

const VALID_PLANNER_KEYWORDS = new Set<string>(PLANNER_KEYWORDS)

const SKILL_EA_TOTAL = 6

const MAX_START_BUFFS = 10
const MIN_BUFF_BASE_ID = 0
const MAX_BUFF_BASE_ID = 9

function collectPresentSinnerKeys(source: Record<string, unknown>): Set<string> {
  const presentKeys = new Set<string>()

  for (const key of Object.keys(source)) {
    const index = Number(key)
    if (!Number.isInteger(index)) continue
    if (index < MIN_EQUIPMENT_SINNER || index > MAX_EQUIPMENT_SINNER) continue
    presentKeys.add(String(index).padStart(2, '0'))
  }

  return presentKeys
}

export function validateEquipment(
  equipment: Record<string, SinnerEquipment>,
): EquipmentValidationError[] {
  const errors: EquipmentValidationError[] = []

  const presentKeys = collectPresentSinnerKeys(equipment)

  const missingSinners = ALL_SINNER_KEYS.filter((key) => !presentKeys.has(key))
  if (missingSinners.length > 0) {
    errors.push({
      code: 'EQUIPMENT_MISSING_SINNER',
      message: `Missing equipment for sinners: ${missingSinners.join(', ')}`,
      field: 'equipment',
      context: { missingSinners },
    })
  }

  for (const sinnerKey of presentKeys) {
    const sinnerEquipment = equipment[sinnerKey] || equipment[String(parseInt(sinnerKey, 10))]
    if (!sinnerEquipment) continue

    if (!sinnerEquipment.identity || !sinnerEquipment.identity.id) {
      errors.push({
        code: 'EQUIPMENT_MISSING_IDENTITY',
        message: `Sinner ${sinnerKey} is missing identity`,
        field: `equipment.${sinnerKey}.identity`,
        context: { sinnerKey },
      })
    }

    if (!sinnerEquipment.egos) {
      errors.push({
        code: 'EQUIPMENT_MISSING_ZAYIN',
        message: `Sinner ${sinnerKey} is missing EGO configuration`,
        field: `equipment.${sinnerKey}.egos`,
        context: { sinnerKey },
      })
      continue
    }

    const egoTypes = Object.keys(sinnerEquipment.egos)
    const validEGOTypes = new Set(EGO_TYPES)
    const invalidTypes = egoTypes.filter(
      (type) => !validEGOTypes.has(type as (typeof EGO_TYPES)[number]),
    )

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

export function validateDeploymentOrder(deploymentOrder: number[]): DeploymentValidationError[] {
  const errors: DeploymentValidationError[] = []

  for (let i = 0; i < deploymentOrder.length; i++) {
    const index = deploymentOrder[i]
    if (
      typeof index !== 'number' ||
      index < MIN_DEPLOYMENT_SINNER ||
      index > MAX_DEPLOYMENT_SINNER
    ) {
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

export function validateSkillEAState(
  skillEAState: Record<string, SkillEAState>,
): SkillEAValidationError[] {
  const errors: SkillEAValidationError[] = []

  const presentKeys = collectPresentSinnerKeys(skillEAState)

  const missingSinners = ALL_SINNER_KEYS.filter((key) => !presentKeys.has(key))
  if (missingSinners.length > 0) {
    errors.push({
      code: 'SKILL_EA_MISSING_SINNER',
      message: `Missing skill EA state for sinners: ${missingSinners.join(', ')}`,
      field: 'skillEAState',
      context: { missingSinners },
    })
  }

  for (const sinnerKey of presentKeys) {
    const sinnerSkills = skillEAState[sinnerKey] || skillEAState[String(parseInt(sinnerKey, 10))]
    if (!sinnerSkills) continue

    const seenSlots = new Set<string>()
    let total = 0

    for (const slotKey of Object.keys(sinnerSkills)) {
      if (!VALID_SKILL_SLOTS.has(slotKey)) {
        errors.push({
          code: 'SKILL_EA_INVALID_SLOT',
          message: `Sinner ${sinnerKey} has invalid skill slot: ${slotKey} (must be 0, 1, or 2)`,
          field: `skillEAState.${sinnerKey}.${slotKey}`,
          context: { sinnerKey, slotKey },
        })
        continue
      }

      if (seenSlots.has(slotKey)) {
        errors.push({
          code: 'SKILL_EA_DUPLICATE_SLOT',
          message: `Sinner ${sinnerKey} has duplicate skill slot: ${slotKey}`,
          field: `skillEAState.${sinnerKey}`,
          context: { sinnerKey, slotKey },
        })
      }
      seenSlots.add(slotKey)

      // A stored non-number would turn the running total into a string, so the
      // mismatch is reported by the total check below rather than concatenated.
      const ea = sinnerSkills[slotKey as unknown as (typeof OFFENSIVE_SKILL_SLOTS)[number]]
      if (typeof ea !== 'number' || !Number.isFinite(ea)) continue
      total += ea
    }

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

export function validateGiftIdArray(
  giftIds: string[],
  fieldName: string,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
): GiftValidationError[] {
  const errors: GiftValidationError[] = []
  const seen = new Set<string>()

  for (const [i, giftId] of giftIds.entries()) {
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
      const parsed = EncodedGiftIdSchema.safeParse(giftId)
      if (!parsed.success || !(decodeGiftSelection(parsed.data).giftId in egoGiftSpec)) {
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

  if (buffIds.length > MAX_START_BUFFS) {
    errors.push({
      code: 'BUFF_EXCEEDS_MAX',
      message: `Start buffs count ${buffIds.length} exceeds maximum ${MAX_START_BUFFS}`,
      field: 'selectedBuffIds',
      context: { count: buffIds.length, max: MAX_START_BUFFS },
    })
  }

  const seenBaseIds = new Set<number>()

  for (const [i, buffId] of buffIds.entries()) {
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

export function validateStartGiftSelection(
  selectedGiftKeyword: string | null,
  selectedGiftIds: string[],
): StartGiftValidationError[] {
  const errors: StartGiftValidationError[] = []

  if (!selectedGiftKeyword && selectedGiftIds.length > 0) {
    errors.push({
      code: 'START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS',
      message: `Start gift IDs are selected but no keyword is set`,
      field: 'selectedGiftIds',
      context: { giftCount: selectedGiftIds.length },
    })
  }

  const seen = new Set<string>()
  for (const [i, giftId] of selectedGiftIds.entries()) {
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

export function validateFloorThemePacksForSave(
  floorSelections: FloorThemeSelection[],
  floorCount: number,
): FloorValidationError[] {
  const errors: FloorValidationError[] = []

  const seenThemePackIds = new Map<string, number>()

  for (let i = 0; i < floorCount; i++) {
    const floor = floorSelections[i]
    const floorNumber = i + 1

    if (!floor?.themePackId) {
      errors.push({
        code: 'FLOOR_MISSING_THEME_PACK',
        message: `Floor ${floorNumber} must have a theme pack selected`,
        field: `floorSelections[${i}].themePackId`,
        floorIndex: i,
        floorNumber,
      })
      continue
    }

    if (i > 0) {
      const previousFloor = floorSelections[i - 1]
      if (!previousFloor?.themePackId) {
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

    const giftIds = Array.from(floor.giftIds)
    const seenGiftIds = new Set<string>()
    for (const [j, giftId] of giftIds.entries()) {
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

function validateFloorDifficulties(
  floorSelections: FloorThemeSelection[],
  category: MDCategory,
  floorCount: number,
): DifficultyValidationError[] {
  const errors: DifficultyValidationError[] = []
  const allowedByFloor = ALLOWED_FLOOR_DIFFICULTIES[category]

  for (let i = 0; i < floorCount; i++) {
    const floor = floorSelections[i]
    if (!floor) continue

    const allowed = allowedByFloor[i]
    if (!allowed || allowed.includes(floor.difficulty)) continue

    const floorNumber = i + 1
    const expected = allowed.map((idx) => DUNGEON_NAME_BY_IDX.get(idx)).join(' or ')
    errors.push({
      code: 'DIFFICULTY_INVALID_FOR_CATEGORY',
      message: `Floor ${floorNumber} must be ${expected} for ${category} category`,
      field: `floorSelections[${i}].difficulty`,
      floorIndex: i,
      floorNumber,
    })
  }

  return errors
}

function validateFloorGiftExistence(
  floorSelections: FloorThemeSelection[],
  floorCount: number,
  egoGiftSpec: Record<string, EGOGiftSpec>,
): FloorValidationError[] {
  return floorSelections.slice(0, floorCount).flatMap((floor, i) => {
    const unknownIds: string[] = []

    for (const giftId of floor.giftIds) {
      const parsed = EncodedGiftIdSchema.safeParse(giftId)
      if (!parsed.success || !hasGiftId(parsed.data, egoGiftSpec)) {
        unknownIds.push(giftId)
      }
    }

    if (unknownIds.length === 0) return []

    const floorNumber = i + 1
    return [
      {
        code: 'FLOOR_UNKNOWN_GIFT_ID' as const,
        message: `Floor ${floorNumber}: unknown gift ID(s): ${unknownIds.join(', ')}`,
        field: `floorSelections[${i}].giftIds`,
        floorIndex: i,
        floorNumber,
        context: { giftIds: unknownIds },
      },
    ]
  })
}

function validateFloorGiftAffordability(
  floorSelections: FloorThemeSelection[],
  floorCount: number,
  egoGiftSpec: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>,
): FloorValidationError[] {
  return floorSelections.slice(0, floorCount).flatMap((floor, i) => {
    if (!floor?.themePackId) return []

    const unaffordableIds = getUnaffordableGiftIds(floor.giftIds, floor.themePackId, egoGiftSpec)

    if (unaffordableIds.length === 0) return []

    const floorNumber = i + 1
    const giftNames = unaffordableIds.map((id) => giftDisplayName(id, egoGiftI18n ?? {})).join(', ')

    return [
      {
        code: 'FLOOR_UNAFFORDABLE_GIFT' as const,
        message: `Floor ${floorNumber} has ${unaffordableIds.length} gift(s) not available for theme pack: ${giftNames}`,
        field: `floorSelections[${i}].giftIds`,
        floorIndex: i,
        floorNumber,
        context: { giftIds: unaffordableIds, giftNames, themePackId: floor.themePackId },
      },
    ]
  })
}

export function validateSelectedKeywords(keywords: string[]): KeywordValidationError[] {
  const errors: KeywordValidationError[] = []
  for (const keyword of keywords) {
    if (!VALID_PLANNER_KEYWORDS.has(keyword)) {
      errors.push({
        code: 'KEYWORD_INVALID',
        message: `Selected keyword "${keyword}" is not a valid planner keyword`,
        field: 'selectedKeywords',
        context: { keyword },
      })
    }
  }
  return errors
}

export function validateEntityIds(
  content: MDPlannerContent,
  category: MDCategory,
  registry: PlannerIdRegistry,
): EntityIdValidationError[] {
  const errors: EntityIdValidationError[] = []

  for (const [sinnerKey, sinnerEquipment] of Object.entries(content.equipment ?? {})) {
    const identityId = sinnerEquipment?.identity?.id
    if (identityId && !registry.identityIds.has(identityId)) {
      errors.push({
        code: 'IDENTITY_UNKNOWN_ID',
        message: `Sinner ${sinnerKey} has unknown identity ID '${identityId}'`,
        field: `equipment.${sinnerKey}.identity.id`,
        context: { id: identityId },
      })
    }

    for (const [egoType, ego] of Object.entries(sinnerEquipment?.egos ?? {})) {
      const egoId = ego?.id
      if (egoId && !registry.egoIds.has(egoId)) {
        errors.push({
          code: 'EGO_UNKNOWN_ID',
          message: `Sinner ${sinnerKey} has unknown ${egoType} EGO ID '${egoId}'`,
          field: `equipment.${sinnerKey}.egos.${egoType}.id`,
          context: { id: egoId },
        })
      }
    }
  }

  for (const [i, floor] of content.floorSelections.slice(0, FLOOR_COUNTS[category]).entries()) {
    const themePackId = floor?.themePackId
    if (themePackId && !registry.themePackIds.has(themePackId)) {
      errors.push({
        code: 'THEME_PACK_UNKNOWN_ID',
        message: `Floor ${i + 1} has unknown theme pack ID '${themePackId}'`,
        field: `floorSelections[${i}].themePackId`,
        context: { id: themePackId },
      })
    }
  }

  for (const [i, buffId] of content.selectedBuffIds.entries()) {
    if (!registry.startBuffIds.has(String(buffId))) {
      errors.push({
        code: 'START_BUFF_UNKNOWN_ID',
        message: `Start buff ID ${buffId} is unknown`,
        field: `selectedBuffIds[${i}]`,
        context: { id: String(buffId) },
      })
    }
  }

  return errors
}

export function validatePlannerForPublish(
  title: string | undefined,
  content: MDPlannerContent,
  category: MDCategory,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>,
  registry?: PlannerIdRegistry,
): { isValid: boolean; errors: PlannerValidationError[] } {
  const errors: PlannerValidationError[] = []

  if (!title || title.trim() === '') {
    errors.push({
      code: 'MISSING_TITLE',
      message: 'Title is required for publishing',
      field: 'title',
    })
  }

  errors.push(...validateEquipment(content.equipment))

  if (registry) {
    errors.push(...validateEntityIds(content, category, registry))
  }

  errors.push(...validateDeploymentOrder(content.deploymentOrder))

  errors.push(...validateSkillEAState(content.skillEAState))

  errors.push(...validateGiftIdArray(content.selectedGiftIds, 'selectedGiftIds', egoGiftSpec))
  errors.push(...validateGiftIdArray(content.observationGiftIds, 'observationGiftIds', egoGiftSpec))
  errors.push(
    ...validateGiftIdArray(content.comprehensiveGiftIds, 'comprehensiveGiftIds', egoGiftSpec),
  )

  errors.push(...validateStartBuffIds(content.selectedBuffIds))

  errors.push(...validateStartGiftSelection(content.selectedGiftKeyword, content.selectedGiftIds))

  errors.push(...validateSelectedKeywords(migrateKeywords(content.selectedKeywords)))

  const floorCount = FLOOR_COUNTS[category]
  const deserializedFloorSelections: FloorThemeSelection[] = content.floorSelections.map(
    (floor) => ({
      ...floor,
      giftIds: new Set(floor.giftIds),
    }),
  )
  errors.push(...validateFloorThemePacksForSave(deserializedFloorSelections, floorCount))

  errors.push(...validateFloorDifficulties(deserializedFloorSelections, category, floorCount))

  if (egoGiftSpec) {
    errors.push(...validateFloorGiftExistence(deserializedFloorSelections, floorCount, egoGiftSpec))
  }

  if (egoGiftSpec) {
    errors.push(
      ...validateFloorGiftAffordability(
        deserializedFloorSelections,
        floorCount,
        egoGiftSpec,
        egoGiftI18n,
      ),
    )
  }

  return {
    isValid: errors.length === 0,
    errors,
  }
}

export function validatePlannerForDraftSave(
  content: MDPlannerContent,
  category: MDCategory,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>,
  registry?: PlannerIdRegistry,
): { key: string; params?: Record<string, string> } | null {
  const errors: PlannerValidationError[] = [
    ...validateEquipment(content.equipment),

    ...(registry ? validateEntityIds(content, category, registry) : []),

    ...validateDeploymentOrder(content.deploymentOrder),

    ...validateSkillEAState(content.skillEAState),

    ...validateGiftIdArray(content.selectedGiftIds, 'selectedGiftIds', egoGiftSpec),
    ...validateGiftIdArray(content.observationGiftIds, 'observationGiftIds', egoGiftSpec),
    ...validateGiftIdArray(content.comprehensiveGiftIds, 'comprehensiveGiftIds', egoGiftSpec),

    ...validateStartBuffIds(content.selectedBuffIds),

    ...validateStartGiftSelection(content.selectedGiftKeyword, content.selectedGiftIds),
  ]

  const floorCount = FLOOR_COUNTS[category]
  const deserializedFloorSelections: FloorThemeSelection[] = content.floorSelections.map(
    (floor) => ({
      ...floor,
      giftIds: new Set(floor.giftIds),
    }),
  )
  const floorErrors = validateFloorThemePacksForSave(deserializedFloorSelections, floorCount)
  errors.push(...floorErrors.filter((e) => e.code !== 'FLOOR_MISSING_THEME_PACK'))

  if (egoGiftSpec) {
    errors.push(...validateFloorGiftExistence(deserializedFloorSelections, floorCount, egoGiftSpec))
  }

  if (egoGiftSpec) {
    errors.push(
      ...validateFloorGiftAffordability(
        deserializedFloorSelections,
        floorCount,
        egoGiftSpec,
        egoGiftI18n,
      ),
    )
  }

  const [firstError] = errors
  if (firstError === undefined) return null
  return toUserFriendlyError(firstError)
}

export function validatePlannerForImport(
  planner: SaveablePlanner,
  egoGiftSpec: Record<string, EGOGiftSpec>,
  registry: PlannerIdRegistry,
): { key: string; params?: Record<string, string> } | null {
  if (!isMDPlanner(planner)) return null

  const { content } = planner
  const { category } = planner.config

  if (planner.metadata.published) {
    const [firstError] = validatePlannerForPublish(
      planner.metadata.title,
      content,
      category,
      egoGiftSpec,
      undefined,
      registry,
    ).errors
    return firstError ? toUserFriendlyError(firstError) : null
  }

  return validatePlannerForDraftSave(content, category, egoGiftSpec, undefined, registry)
}

export function validateNoteSizes(
  sectionNotes: Record<string, { content: unknown }>,
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
