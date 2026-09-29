import {
  EGO_TYPES,
  REQUIRED_EGO_TYPE,
  OFFENSIVE_SKILL_SLOTS,
  PLANNER_KEYWORDS,
  FLOOR_RULE_TABLE,
  migrateKeywords,
  EncodedGiftIdSchema,
} from '@/shared/gameData'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import { decodeGiftSelection, giftDisplayName, hasGiftId } from '@/pages/egoGift'
import { measureDocBytes } from '@/shared/noteEditor'
import { renderContentPath } from './validation/contentPath'
import type { Codes } from './validation/codes'
import { admitFloors, parseFloors } from './validation/floor/floorRules'
import { floorReferenceViolations } from './validation/floor/floorReferences'
import { getUnaffordableGiftIds } from './plannerRules'
import type { JSONContent } from '@tiptap/core'
import { isMDPlanner } from '../types/PlannerTypes'
import type { MDPlannerContent, SaveablePlanner } from '../types/PlannerTypes'
import type { SinnerEquipment, SkillEAState } from '../types/DeckTypes'
import type { FloorRuleStage, MDCategory } from '@/shared/gameData'
import type { EGOGiftSpec } from '@/pages/egoGift'
import type {
  FloorReferenceData,
  FloorReferenceViolation,
} from './validation/floor/floorReferences'
import type {
  PlannerValidationError,
  EquipmentValidationError,
  DeploymentValidationError,
  SkillEAValidationError,
  GiftValidationError,
  BuffValidationError,
  StartGiftValidationError,
  FloorRuleValidationError,
  FloorUnknownGiftValidationError,
  GiftNotAffordableValidationError,
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
      code: 'MISSING_SINNER',
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
        code: 'MISSING_IDENTITY',
        message: `Sinner ${sinnerKey} is missing identity`,
        field: `equipment.${sinnerKey}.identity`,
        context: { sinnerKey },
      })
    }

    if (!sinnerEquipment.egos) {
      errors.push({
        code: 'MISSING_EGO_ZAYIN',
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
        code: 'INVALID_EGO_TYPE',
        message: `Sinner ${sinnerKey} has invalid EGO types: ${invalidTypes.join(', ')}`,
        field: `equipment.${sinnerKey}.egos`,
        context: { sinnerKey, invalidTypes },
      })
    }

    if (egoTypes.length > EGO_TYPES.length) {
      errors.push({
        code: 'INVALID_EGO_TYPE',
        message: `Sinner ${sinnerKey} has more than ${EGO_TYPES.length} EGO types`,
        field: `equipment.${sinnerKey}.egos`,
        context: { sinnerKey, count: egoTypes.length },
      })
    }

    if (!sinnerEquipment.egos[REQUIRED_EGO_TYPE]) {
      errors.push({
        code: 'MISSING_EGO_ZAYIN',
        message: `Sinner ${sinnerKey} is missing required ${REQUIRED_EGO_TYPE} EGO`,
        field: `equipment.${sinnerKey}.egos.${REQUIRED_EGO_TYPE}`,
        context: { sinnerKey },
      })
    } else if (!sinnerEquipment.egos[REQUIRED_EGO_TYPE]?.id) {
      errors.push({
        code: 'MISSING_EGO_ZAYIN',
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
        code: typeof index === 'number' ? 'OUT_OF_RANGE_DEPLOYMENT' : 'INVALID_FIELD_TYPE',
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
      code: 'MISSING_SINNER',
      message: `Missing skill EA state for sinners: ${missingSinners.join(', ')}`,
      field: 'skillEAState',
      context: { missingSinners },
    })
  }

  for (const sinnerKey of presentKeys) {
    const sinnerSkills = skillEAState[sinnerKey] || skillEAState[String(parseInt(sinnerKey, 10))]
    if (!sinnerSkills) continue

    let total = 0

    for (const slotKey of Object.keys(sinnerSkills)) {
      if (!VALID_SKILL_SLOTS.has(slotKey)) {
        errors.push({
          code: 'INVALID_SKILL_SLOT',
          message: `Sinner ${sinnerKey} has invalid skill slot: ${slotKey} (must be 0, 1, or 2)`,
          field: `skillEAState.${sinnerKey}.${slotKey}`,
          context: { sinnerKey, slotKey },
        })
        continue
      }

      // A stored non-number would turn the running total into a string, so the
      // mismatch is reported by the total check below rather than concatenated.
      const ea = sinnerSkills[slotKey as unknown as (typeof OFFENSIVE_SKILL_SLOTS)[number]]
      if (typeof ea !== 'number' || !Number.isFinite(ea)) continue
      total += ea
    }

    if (total !== SKILL_EA_TOTAL) {
      errors.push({
        code: 'OUT_OF_RANGE_SKILL_TOTAL',
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
  duplicateCode: Codes<'DUPLICATE_EGOGIFT' | 'DUPLICATE_STARTGIFT'>,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
): GiftValidationError[] {
  const errors: GiftValidationError[] = []
  const seen = new Set<string>()

  for (const [i, giftId] of giftIds.entries()) {
    if (seen.has(giftId)) {
      errors.push({
        code: duplicateCode,
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
          code: 'UNKNOWN_EGOGIFT',
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
      code: 'EXCESS_STARTBUFF',
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
        code: 'OUT_OF_RANGE_STARTBUFF',
        message: `Start buff ID ${buffId} has invalid base ID ${baseId} (must be 00-09)`,
        field: `selectedBuffIds[${i}]`,
        context: { buffId, baseId, index: i },
      })
      continue
    }

    if (seenBaseIds.has(baseId)) {
      errors.push({
        code: 'DUPLICATE_STARTBUFF',
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
      code: 'MISSING_STARTGIFT_KEYWORD',
      message: `Start gift IDs are selected but no keyword is set`,
      field: 'selectedGiftIds',
      context: { giftCount: selectedGiftIds.length },
    })
  }

  return errors
}

export function validateSelectedKeywords(keywords: string[]): KeywordValidationError[] {
  const errors: KeywordValidationError[] = []
  for (const keyword of keywords) {
    if (!VALID_PLANNER_KEYWORDS.has(keyword)) {
      errors.push({
        code: 'UNKNOWN_KEYWORD',
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
  registry: PlannerIdRegistry,
): EntityIdValidationError[] {
  const errors: EntityIdValidationError[] = []

  for (const [sinnerKey, sinnerEquipment] of Object.entries(content.equipment ?? {})) {
    const identityId = sinnerEquipment?.identity?.id
    if (identityId && !registry.identityIds.has(identityId)) {
      errors.push({
        code: 'UNKNOWN_IDENTITY',
        message: `Sinner ${sinnerKey} has unknown identity ID '${identityId}'`,
        field: `equipment.${sinnerKey}.identity.id`,
        context: { id: identityId },
      })
    }

    for (const [egoType, ego] of Object.entries(sinnerEquipment?.egos ?? {})) {
      const egoId = ego?.id
      if (egoId && !registry.egoIds.has(egoId)) {
        errors.push({
          code: 'UNKNOWN_EGO',
          message: `Sinner ${sinnerKey} has unknown ${egoType} EGO ID '${egoId}'`,
          field: `equipment.${sinnerKey}.egos.${egoType}.id`,
          context: { id: egoId },
        })
      }
    }
  }

  for (const [i, buffId] of content.selectedBuffIds.entries()) {
    if (!registry.startBuffIds.has(String(buffId))) {
      errors.push({
        code: 'UNKNOWN_STARTBUFF',
        message: `Start buff ID ${buffId} is unknown`,
        field: `selectedBuffIds[${i}]`,
        context: { id: String(buffId) },
      })
    }
  }

  return errors
}

function knownGift(giftId: string, egoGiftSpec: Record<string, EGOGiftSpec>): boolean {
  const encoded = EncodedGiftIdSchema.safeParse(giftId)
  return encoded.success && hasGiftId(encoded.data, egoGiftSpec)
}

function affordableGift(
  giftId: string,
  themePackId: string,
  egoGiftSpec: Record<string, EGOGiftSpec>,
): boolean {
  const encoded = EncodedGiftIdSchema.safeParse(giftId)
  return (
    !encoded.success ||
    getUnaffordableGiftIds(new Set([encoded.data]), themePackId, egoGiftSpec).length === 0
  )
}

function floorReferenceData(
  egoGiftSpec: Record<string, EGOGiftSpec> | undefined,
  registry: PlannerIdRegistry | undefined,
): FloorReferenceData {
  return {
    hasThemePack: (themePackId) => registry === undefined || registry.themePackIds.has(themePackId),
    hasEgoGift: (giftId) => egoGiftSpec === undefined || knownGift(giftId, egoGiftSpec),
    isGiftAffordableForThemePack: (giftId, themePackId) =>
      egoGiftSpec === undefined || affordableGift(giftId, themePackId, egoGiftSpec),
  }
}

function giftName(giftId: string, egoGiftI18n: Record<string, string>): string {
  const encoded = EncodedGiftIdSchema.safeParse(giftId)
  return encoded.success ? giftDisplayName(encoded.data, egoGiftI18n) : giftId
}

function floorReferenceError(
  violation: FloorReferenceViolation,
  egoGiftI18n: Record<string, string>,
): EntityIdValidationError | FloorUnknownGiftValidationError | GiftNotAffordableValidationError {
  const message = `${violation.code} at ${renderContentPath(violation.path)}`
  switch (violation.code) {
    case 'UNKNOWN_THEMEPACK':
      return {
        code: violation.code,
        message,
        field: renderContentPath(violation.path),
        context: { id: violation.themePackId },
      }
    case 'UNKNOWN_EGOGIFT_FLOOR':
      return {
        code: violation.code,
        message,
        path: violation.path,
        context: { giftId: violation.giftId },
      }
    case 'UNAFFORDABLE_EGOGIFT':
      return {
        code: violation.code,
        message,
        path: violation.path,
        context: {
          giftName: giftName(violation.giftId, egoGiftI18n),
          themePackId: violation.themePackId,
        },
      }
  }
}

function validateFloors(
  rawFloorSelections: unknown,
  category: MDCategory,
  stage: FloorRuleStage,
  egoGiftSpec: Record<string, EGOGiftSpec> | undefined,
  egoGiftI18n: Record<string, string> | undefined,
  registry: PlannerIdRegistry | undefined,
): PlannerValidationError[] {
  const parsed = parseFloors(rawFloorSelections, FLOOR_RULE_TABLE.categories[category])
  const admission = admitFloors(parsed, stage)
  const ruleErrors: FloorRuleValidationError[] = (
    admission.ok ? admission.boundaryViolations : admission.violations
  ).map((violation) => ({
    code: violation.code,
    message: `${violation.code} at ${renderContentPath(violation.path)}`,
    path: violation.path,
  }))
  const referenceErrors = floorReferenceViolations(
    parsed,
    floorReferenceData(egoGiftSpec, registry),
  ).map((violation) => floorReferenceError(violation, egoGiftI18n ?? {}))

  return [...ruleErrors, ...referenceErrors]
}

export type PlannerValidationResult = { isValid: boolean; errors: PlannerValidationError[] }

const toResult = (errors: PlannerValidationError[]): PlannerValidationResult => ({
  isValid: errors.length === 0,
  errors,
})

export function validatePlannerForPublish(
  title: string | undefined,
  content: MDPlannerContent,
  category: MDCategory,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>,
  registry?: PlannerIdRegistry,
): PlannerValidationResult {
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
    errors.push(...validateEntityIds(content, registry))
  }

  errors.push(...validateDeploymentOrder(content.deploymentOrder))

  errors.push(...validateSkillEAState(content.skillEAState))

  errors.push(
    ...validateGiftIdArray(
      content.selectedGiftIds,
      'selectedGiftIds',
      'DUPLICATE_STARTGIFT',
      egoGiftSpec,
    ),
  )
  errors.push(
    ...validateGiftIdArray(
      content.observationGiftIds,
      'observationGiftIds',
      'DUPLICATE_EGOGIFT',
      egoGiftSpec,
    ),
  )
  errors.push(
    ...validateGiftIdArray(
      content.comprehensiveGiftIds,
      'comprehensiveGiftIds',
      'DUPLICATE_EGOGIFT',
      egoGiftSpec,
    ),
  )

  errors.push(...validateStartBuffIds(content.selectedBuffIds))

  errors.push(...validateStartGiftSelection(content.selectedGiftKeyword, content.selectedGiftIds))

  errors.push(...validateSelectedKeywords(migrateKeywords(content.selectedKeywords)))

  errors.push(
    ...validateFloors(
      content.floorSelections,
      category,
      'publish',
      egoGiftSpec,
      egoGiftI18n,
      registry,
    ),
  )

  return toResult(errors)
}

export function validatePlannerForDraftSave(
  content: MDPlannerContent,
  category: MDCategory,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>,
  registry?: PlannerIdRegistry,
): PlannerValidationResult {
  return toResult([
    ...validateEquipment(content.equipment),

    ...(registry ? validateEntityIds(content, registry) : []),

    ...validateDeploymentOrder(content.deploymentOrder),

    ...validateSkillEAState(content.skillEAState),

    ...validateGiftIdArray(
      content.selectedGiftIds,
      'selectedGiftIds',
      'DUPLICATE_STARTGIFT',
      egoGiftSpec,
    ),
    ...validateGiftIdArray(
      content.observationGiftIds,
      'observationGiftIds',
      'DUPLICATE_EGOGIFT',
      egoGiftSpec,
    ),
    ...validateGiftIdArray(
      content.comprehensiveGiftIds,
      'comprehensiveGiftIds',
      'DUPLICATE_EGOGIFT',
      egoGiftSpec,
    ),

    ...validateStartBuffIds(content.selectedBuffIds),

    ...validateStartGiftSelection(content.selectedGiftKeyword, content.selectedGiftIds),

    ...validateFloors(
      content.floorSelections,
      category,
      'draft',
      egoGiftSpec,
      egoGiftI18n,
      registry,
    ),
  ])
}

export function validatePlannerForImport(
  planner: SaveablePlanner,
  egoGiftSpec: Record<string, EGOGiftSpec>,
  registry: PlannerIdRegistry,
): PlannerValidationResult {
  if (!isMDPlanner(planner)) return toResult([])

  const { content } = planner
  const { category } = planner.config

  if (planner.metadata.published) {
    return validatePlannerForPublish(
      planner.metadata.title,
      content,
      category,
      egoGiftSpec,
      undefined,
      registry,
    )
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
