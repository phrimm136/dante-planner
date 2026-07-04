/**
 * Planner Validation Error Types
 *
 * The structured validation error hierarchy produced by planner validators,
 * plus the mapping from a structured error to a user-facing i18n key + params.
 */

/**
 * Base validation error with context
 */
export interface ValidationError {
  /** Error code for programmatic handling */
  code: string
  /** Human-readable error message */
  message: string
  /** Optional field path for locating the error */
  field?: string
  /** Optional additional context */
  context?: Record<string, unknown>
}

/**
 * Equipment validation error
 */
export interface EquipmentValidationError extends ValidationError {
  code:
    | 'EQUIPMENT_MISSING_SINNER'
    | 'EQUIPMENT_MISSING_IDENTITY'
    | 'EQUIPMENT_MISSING_ZAYIN'
    | 'EQUIPMENT_INVALID_EGO_TYPES'
    | 'EQUIPMENT_INVALID_ID_FORMAT'
}

/**
 * Deployment order validation error
 */
export interface DeploymentValidationError extends ValidationError {
  code: 'DEPLOYMENT_INVALID_INDEX'
}

/**
 * Skill EA validation error
 */
export interface SkillEAValidationError extends ValidationError {
  code:
    | 'SKILL_EA_MISSING_SINNER'
    | 'SKILL_EA_INVALID_SLOT'
    | 'SKILL_EA_DUPLICATE_SLOT'
    | 'SKILL_EA_INVALID_TOTAL'
}

/**
 * Gift IDs validation error
 */
export interface GiftValidationError extends ValidationError {
  code: 'GIFT_DUPLICATE_ID' | 'GIFT_UNKNOWN_ID'
}

/**
 * Start buff validation error
 */
export interface BuffValidationError extends ValidationError {
  code: 'BUFF_EXCEEDS_MAX' | 'BUFF_DUPLICATE_BASE_ID' | 'BUFF_INVALID_FORMAT'
}

/**
 * Start gift validation error
 */
export interface StartGiftValidationError extends ValidationError {
  code: 'START_GIFT_NO_KEYWORD_BUT_HAS_GIFTS' | 'START_GIFT_DUPLICATE_ID'
}

/**
 * Floor validation error — carries the failing floor and offending gift/theme-pack context.
 */
export interface FloorValidationError extends ValidationError {
  code:
    | 'FLOOR_MISSING_THEME_PACK'
    | 'FLOOR_PREREQUISITE_VIOLATION'
    | 'FLOOR_DUPLICATE_GIFT_ID'
    | 'FLOOR_DUPLICATE_THEME_PACK'
    | 'FLOOR_UNAFFORDABLE_GIFT'
    | 'GIFT_UNKNOWN_ID'
  /** 0-indexed floor that failed validation */
  floorIndex?: number
  /** 1-indexed floor number for display */
  floorNumber?: number
}

/**
 * Difficulty validation error (for published planners)
 */
export interface DifficultyValidationError extends ValidationError {
  code: 'DIFFICULTY_INVALID_FOR_CATEGORY'
  /** 0-indexed floor that failed validation */
  floorIndex?: number
  /** 1-indexed floor number for display */
  floorNumber?: number
}

/**
 * Title validation error (strict/publish mode only)
 */
export interface TitleValidationError extends ValidationError {
  code: 'MISSING_TITLE'
}

/**
 * Union type of all validation errors
 */
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

/**
 * Maps a structured validation error to an i18n key + params for toast display
 *
 * Structural errors (equipment, deployment, skill EA, gift IDs, buffs, start gifts)
 * indicate corrupted planner state — the user cannot meaningfully act on them, so
 * they collapse to a single generic key. Actionable errors (title, theme pack,
 * difficulty, affordability) get their own specific keys.
 */
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
    case 'GIFT_UNKNOWN_ID': {
      const floorError = error as FloorValidationError
      const ctx = floorError.context as { giftIds?: string[] } | undefined
      return {
        key: 'pages.plannerMD.validation.unknownGiftId',
        params: {
          floor: String(floorError.floorNumber ?? ''),
          gifts: ctx?.giftIds?.join(', ') ?? '',
        },
      }
    }
    case 'DIFFICULTY_INVALID_FOR_CATEGORY':
      return { key: 'pages.plannerMD.publish.requiresHardMode' }
    default:
      return { key: 'pages.plannerMD.validation.corruptedState' }
  }
}
