import { validationAppError } from '@/lib/apiErrorClassifier'
import type { Codes, ValidationCode } from './validation/codes'
import type { ContentPath } from './validation/contentPath'
import type { FloorRuleCode } from './validation/violation'

export interface ValidationError {
  code: ValidationCode
  message: string
  field?: string
  context?: Record<string, unknown>
}

export interface EquipmentValidationError extends ValidationError {
  code: Codes<'MISSING_SINNER' | 'MISSING_IDENTITY' | 'MISSING_EGO_ZAYIN' | 'INVALID_EGO_TYPE'>
}

export interface DeploymentValidationError extends ValidationError {
  code: Codes<'OUT_OF_RANGE_DEPLOYMENT' | 'INVALID_FIELD_TYPE'>
}

export interface SkillEAValidationError extends ValidationError {
  code: Codes<'MISSING_SINNER' | 'INVALID_SKILL_SLOT' | 'OUT_OF_RANGE_SKILL_TOTAL'>
}

export interface GiftValidationError extends ValidationError {
  code: Codes<'DUPLICATE_EGOGIFT' | 'DUPLICATE_STARTGIFT' | 'UNKNOWN_EGOGIFT'>
}

export interface BuffValidationError extends ValidationError {
  code: Codes<'EXCESS_STARTBUFF' | 'DUPLICATE_STARTBUFF' | 'OUT_OF_RANGE_STARTBUFF'>
}

export interface StartGiftValidationError extends ValidationError {
  code: Codes<'MISSING_STARTGIFT_KEYWORD'>
}

export interface FloorRuleValidationError extends ValidationError {
  code: FloorRuleCode
  path: ContentPath
}

export interface FloorUnknownGiftValidationError extends ValidationError {
  code: Codes<'UNKNOWN_EGOGIFT_FLOOR'>
  path: ContentPath
  context: { giftId: string }
}

export interface GiftNotAffordableValidationError extends ValidationError {
  code: Codes<'UNAFFORDABLE_EGOGIFT'>
  path: ContentPath
  context: { giftName: string; themePackId: string }
}

export interface TitleValidationError extends ValidationError {
  code: Codes<'MISSING_TITLE'>
}

export interface KeywordValidationError extends ValidationError {
  code: Codes<'UNKNOWN_KEYWORD'>
  context: { keyword: string }
}

export interface EntityIdValidationError extends ValidationError {
  code: Codes<'UNKNOWN_IDENTITY' | 'UNKNOWN_EGO' | 'UNKNOWN_THEMEPACK' | 'UNKNOWN_STARTBUFF'>
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
  | FloorUnknownGiftValidationError
  | GiftNotAffordableValidationError
  | TitleValidationError
  | KeywordValidationError
  | EntityIdValidationError

const CORRUPTED_STATE = 'pages.plannerMD.validation.corruptedState'

const MESSAGE_KEYS = {
  MISSING_TITLE: 'pages.plannerMD.publish.missingTitle',
  INSUFFICIENT_FLOORS: 'pages.plannerMD.publish.missingThemePack',
  MISSING_THEMEPACK: 'pages.plannerMD.publish.missingThemePack',
  OUT_OF_ORDER_THEMEPACK: 'pages.plannerMD.previousFloorNoThemePack',
  OUT_OF_ORDER_DIFFICULTY: 'pages.plannerMD.publish.normalAfterHard',
  INSUFFICIENT_DIFFICULTY_ENTRY: 'pages.plannerMD.publish.requiresHardMode',
  OUT_OF_RANGE_DIFFICULTY_NOT_HARD: 'pages.plannerMD.publish.hardModeOnly',
  OUT_OF_RANGE_DIFFICULTY_NOT_EXTREME: 'pages.plannerMD.publish.extremeModeOnly',
  OUT_OF_RANGE_DIFFICULTY: CORRUPTED_STATE,
  MISSING_SINNER: CORRUPTED_STATE,
  MISSING_IDENTITY: CORRUPTED_STATE,
  MISSING_EGO_ZAYIN: CORRUPTED_STATE,
  INVALID_EGO_TYPE: CORRUPTED_STATE,
  OUT_OF_RANGE_DEPLOYMENT: CORRUPTED_STATE,
  INVALID_SKILL_SLOT: CORRUPTED_STATE,
  OUT_OF_RANGE_SKILL_TOTAL: CORRUPTED_STATE,
  DUPLICATE_EGOGIFT: CORRUPTED_STATE,
  DUPLICATE_STARTGIFT: CORRUPTED_STATE,
  UNKNOWN_EGOGIFT: CORRUPTED_STATE,
  EXCESS_STARTBUFF: CORRUPTED_STATE,
  DUPLICATE_STARTBUFF: CORRUPTED_STATE,
  OUT_OF_RANGE_STARTBUFF: CORRUPTED_STATE,
  MISSING_STARTGIFT_KEYWORD: CORRUPTED_STATE,
  INVALID_FIELD_TYPE: CORRUPTED_STATE,
  DUPLICATE_THEMEPACK: CORRUPTED_STATE,
  UNAFFORDABLE_EGOGIFT: 'pages.plannerMD.publish.themePackEgoGiftInconsistency',
  UNKNOWN_EGOGIFT_FLOOR: 'pages.plannerMD.validation.unknownGiftId',
  UNKNOWN_KEYWORD: 'pages.plannerMD.validation.invalidKeyword',
  UNKNOWN_IDENTITY: 'pages.plannerMD.validation.unknownIdentityId',
  UNKNOWN_EGO: 'pages.plannerMD.validation.unknownEgoId',
  UNKNOWN_THEMEPACK: 'pages.plannerMD.validation.unknownThemePackId',
  UNKNOWN_STARTBUFF: 'pages.plannerMD.validation.unknownStartBuffId',
} as const satisfies Record<PlannerValidationError['code'], string>

export const validationMessageKey = (code: PlannerValidationError['code']): string =>
  MESSAGE_KEYS[code]

function floorNumberOf([, floor]: ContentPath): string {
  return floor?.kind === 'index' ? String(floor.value + 1) : ''
}

function messageParams(error: PlannerValidationError): Record<string, string> | undefined {
  switch (error.code) {
    case 'UNAFFORDABLE_EGOGIFT':
      return { pack: error.context.themePackId, gifts: error.context.giftName }
    case 'UNKNOWN_EGOGIFT_FLOOR':
      return { floor: floorNumberOf(error.path), gifts: error.context.giftId }
    case 'UNKNOWN_KEYWORD':
      return { keyword: error.context.keyword }
    case 'UNKNOWN_IDENTITY':
    case 'UNKNOWN_EGO':
    case 'UNKNOWN_THEMEPACK':
    case 'UNKNOWN_STARTBUFF':
      return { id: error.context.id }
    default:
      return undefined
  }
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
  const key = validationMessageKey(error.code)
  const params = messageParams(error)
  return params === undefined ? { key } : { key, params }
}
