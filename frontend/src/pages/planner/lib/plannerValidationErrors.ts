import { validationAppError } from '@/lib/apiErrorClassifier'

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

export interface FloorValidationError extends ValidationError {
  code:
    | 'FLOOR_MISSING_THEME_PACK'
    | 'FLOOR_PREREQUISITE_VIOLATION'
    | 'FLOOR_DUPLICATE_GIFT_ID'
    | 'FLOOR_DUPLICATE_THEME_PACK'
    | 'FLOOR_UNAFFORDABLE_GIFT'
    | 'FLOOR_UNKNOWN_GIFT_ID'
  floorIndex?: number
  floorNumber?: number
}

export interface DifficultyValidationError extends ValidationError {
  code: 'DIFFICULTY_INVALID_FOR_CATEGORY'
  floorIndex?: number
  floorNumber?: number
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
  | FloorValidationError
  | DifficultyValidationError
  | TitleValidationError
  | KeywordValidationError
  | EntityIdValidationError

const UNKNOWN_ENTITY_ID_KEYS = {
  IDENTITY_UNKNOWN_ID: 'pages.plannerMD.validation.unknownIdentityId',
  EGO_UNKNOWN_ID: 'pages.plannerMD.validation.unknownEgoId',
  THEME_PACK_UNKNOWN_ID: 'pages.plannerMD.validation.unknownThemePackId',
  START_BUFF_UNKNOWN_ID: 'pages.plannerMD.validation.unknownStartBuffId',
} as const satisfies Record<EntityIdValidationError['code'], string>

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
    case 'FLOOR_UNAFFORDABLE_GIFT': {
      const floorError = error as FloorValidationError
      const ctx = floorError.context as { giftNames?: string; themePackId?: string } | undefined
      return {
        key: 'pages.plannerMD.publish.themePackEgoGiftInconsistency',
        params: {
          pack: ctx?.themePackId ?? '',
          gifts: ctx?.giftNames ?? '',
        },
      }
    }
    case 'FLOOR_UNKNOWN_GIFT_ID': {
      const ctx = error.context as { giftIds?: string[] } | undefined
      return {
        key: 'pages.plannerMD.validation.unknownGiftId',
        params: {
          floor: String(error.floorNumber ?? ''),
          gifts: ctx?.giftIds?.join(', ') ?? '',
        },
      }
    }
    case 'DIFFICULTY_INVALID_FOR_CATEGORY':
      return { key: 'pages.plannerMD.publish.requiresHardMode' }
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
