import { validationAppError } from '@/lib/apiErrorClassifier'
import { DUNGEON_IDX } from '@/shared/gameData'
import type { DungeonIdx } from '@/shared/gameData'
import type { BeErrorCode } from './floorRules'

export interface ValidationError {
  code: string
  message: string
  field?: string
  context?: Record<string, unknown>
}

export interface EquipmentValidationError extends ValidationError {
  code:
    | 'EQUIPMENT_MISSING_SINNER'
    | 'EQUIPMENT_MISSING_IDENTITY'
    | 'EQUIPMENT_MISSING_ZAYIN'
    | 'EQUIPMENT_INVALID_EGO_TYPES'
    | 'EQUIPMENT_INVALID_ID_FORMAT'
}

export interface DeploymentValidationError extends ValidationError {
  code: 'DEPLOYMENT_INVALID_INDEX'
}

export interface SkillEAValidationError extends ValidationError {
  code:
    | 'SKILL_EA_MISSING_SINNER'
    | 'SKILL_EA_INVALID_SLOT'
    | 'SKILL_EA_DUPLICATE_SLOT'
    | 'SKILL_EA_INVALID_TOTAL'
}

export interface GiftValidationError extends ValidationError {
  code: 'GIFT_DUPLICATE_ID' | 'GIFT_UNKNOWN_ID'
}

export interface BuffValidationError extends ValidationError {
  code: 'BUFF_EXCEEDS_MAX' | 'BUFF_DUPLICATE_BASE_ID' | 'BUFF_INVALID_FORMAT'
}

export interface StartGiftValidationError extends ValidationError {
  code: 'START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS' | 'START_GIFT_DUPLICATE_ID'
}

export interface FloorRuleValidationError extends ValidationError {
  code: Exclude<BeErrorCode, 'VALUE_OUT_OF_RANGE'>
  field: string
}

export interface DifficultyOutOfRangeValidationError extends ValidationError {
  code: 'VALUE_OUT_OF_RANGE'
  field: string
  context: { allowedDifficulties: readonly DungeonIdx[] }
}

export interface FloorUnknownGiftValidationError extends ValidationError {
  code: 'FLOOR_UNKNOWN_GIFT_ID'
  field: string
  floorNumber: number
  context: { giftId: string }
}

export interface GiftNotAffordableValidationError extends ValidationError {
  code: 'GIFT_NOT_AFFORDABLE'
  field: string
  context: { giftName: string; themePackId: string }
}

export interface TitleValidationError extends ValidationError {
  code: 'MISSING_TITLE'
}

export interface KeywordValidationError extends ValidationError {
  code: 'KEYWORD_INVALID'
}

export interface EntityIdValidationError extends ValidationError {
  code: 'IDENTITY_UNKNOWN_ID' | 'EGO_UNKNOWN_ID' | 'THEME_PACK_UNKNOWN_ID' | 'START_BUFF_UNKNOWN_ID'
  context: { id: string }
}

export type PlannerValidationError =
  | EquipmentValidationError
  | DeploymentValidationError
  | SkillEAValidationError
  | GiftValidationError
  | BuffValidationError
  | StartGiftValidationError
  | FloorRuleValidationError
  | DifficultyOutOfRangeValidationError
  | FloorUnknownGiftValidationError
  | GiftNotAffordableValidationError
  | TitleValidationError
  | KeywordValidationError
  | EntityIdValidationError

const UNKNOWN_ENTITY_ID_KEYS = {
  IDENTITY_UNKNOWN_ID: 'pages.plannerMD.validation.unknownIdentityId',
  EGO_UNKNOWN_ID: 'pages.plannerMD.validation.unknownEgoId',
  THEME_PACK_UNKNOWN_ID: 'pages.plannerMD.validation.unknownThemePackId',
  START_BUFF_UNKNOWN_ID: 'pages.plannerMD.validation.unknownStartBuffId',
} as const satisfies Record<EntityIdValidationError['code'], string>

const DIFFICULTY_FIELD_SUFFIX = '.difficulty'

const REQUIRED_DIFFICULTY_KEYS: Partial<Record<DungeonIdx, string>> = {
  [DUNGEON_IDX.HARD]: 'pages.plannerMD.publish.requiresHardMode',
  [DUNGEON_IDX.EXTREME]: 'pages.plannerMD.publish.requiresExtremeMode',
}

function outOfRangeKey(allowedDifficulties: readonly DungeonIdx[]): string {
  const [only, ...rest] = allowedDifficulties
  const key = only !== undefined && rest.length === 0 ? REQUIRED_DIFFICULTY_KEYS[only] : undefined
  return key ?? 'pages.plannerMD.validation.corruptedState'
}

export function plannerValidationError(friendly: { key: string; params?: Record<string, string> }) {
  return validationAppError({
    key: `planner:${friendly.key}`,
    ...(friendly.params !== undefined && { params: friendly.params }),
  })
}

export function toUserFriendlyError(error: PlannerValidationError): {
  key: string
  params?: Record<string, string>
} {
  switch (error.code) {
    case 'MISSING_TITLE':
      return { key: 'pages.plannerMD.publish.missingTitle' }
    case 'FLOOR_MISSING_THEME_PACK':
      return { key: 'pages.plannerMD.publish.missingThemePack' }
    case 'INVALID_SEQUENCE':
      return error.field.endsWith(DIFFICULTY_FIELD_SUFFIX)
        ? { key: 'pages.plannerMD.publish.normalAfterHard' }
        : { key: 'pages.plannerMD.previousFloorNoThemePack' }
    case 'VALUE_OUT_OF_RANGE':
      return { key: outOfRangeKey(error.context.allowedDifficulties) }
    case 'INVALID_FIELD_TYPE':
    case 'FLOOR_DUPLICATE_THEME_PACK':
    case 'DUPLICATE_VALUE':
      return { key: 'pages.plannerMD.validation.corruptedState' }
    case 'GIFT_NOT_AFFORDABLE':
      return {
        key: 'pages.plannerMD.publish.themePackEgoGiftInconsistency',
        params: {
          pack: error.context.themePackId,
          gifts: error.context.giftName,
        },
      }
    case 'FLOOR_UNKNOWN_GIFT_ID':
      return {
        key: 'pages.plannerMD.validation.unknownGiftId',
        params: {
          floor: String(error.floorNumber),
          gifts: error.context.giftId,
        },
      }
    case 'KEYWORD_INVALID': {
      const keywordError = error as KeywordValidationError
      const ctx = keywordError.context as { keyword?: string } | undefined
      return {
        key: 'pages.plannerMD.validation.invalidKeyword',
        params: { keyword: ctx?.keyword ?? '' },
      }
    }
    case 'IDENTITY_UNKNOWN_ID':
    case 'EGO_UNKNOWN_ID':
    case 'THEME_PACK_UNKNOWN_ID':
    case 'START_BUFF_UNKNOWN_ID':
      return { key: UNKNOWN_ENTITY_ID_KEYS[error.code], params: { id: error.context.id } }
    default:
      return { key: 'pages.plannerMD.validation.corruptedState' }
  }
}
