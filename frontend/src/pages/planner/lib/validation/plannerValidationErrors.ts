import { validationAppError } from '@/lib/apiErrorClassifier'
import type { Codes } from './codes'
import type { ContentPath } from './contentPath'
import type { DocumentRefusal, DocumentViolation } from './document'
import type { EgoViolation } from './ego/egos'
import type { GiftIdsViolation } from './egogift/giftIds'
import type { FloorViolation } from './floor/floorRules'
import type { IdentityViolation } from './identity/identity'
import type { DeploymentViolation } from './sinner/deploymentOrder'
import type { SinnerKeyViolation } from './sinner/sinnerKeys'
import type { SkillViolation } from './skill/skillEAState'
import type { StartBuffViolation } from './startbuff/startBuffs'
import type { StartGiftViolation } from './startgift/startGift'

export type FloorReferenceError =
  | { code: Codes<'UNKNOWN_THEMEPACK'>; path: ContentPath; params: { id: string } }
  | {
      code: Codes<'UNKNOWN_EGOGIFT_FLOOR'>
      path: ContentPath
      params: { floor: string; gifts: string }
    }
  | {
      code: Codes<'UNAFFORDABLE_EGOGIFT'>
      path: ContentPath
      params: { pack: string; gifts: string }
    }

export type PlannerValidationError =
  | DocumentRefusal
  | DocumentViolation
  | SinnerKeyViolation
  | IdentityViolation
  | EgoViolation
  | DeploymentViolation
  | SkillViolation
  | GiftIdsViolation
  | StartBuffViolation
  | StartGiftViolation
  | FloorViolation
  | FloorReferenceError

export type FriendlyError = { key: string; params?: Record<string, string> }

const CORRUPTED_STATE = 'pages.plannerMD.validation.corruptedState'
const MISSING_GAME_DATA = 'pages.plannerMD.validation.missingGameData'

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
  OUT_OF_RANGE_SINNER: CORRUPTED_STATE,
  MISSING_SINNER: CORRUPTED_STATE,
  MISSING_IDENTITY: CORRUPTED_STATE,
  MISMATCHED_IDENTITY: CORRUPTED_STATE,
  OUT_OF_RANGE_IDENTITY_LEVEL: CORRUPTED_STATE,
  OUT_OF_RANGE_IDENTITY_UPTIE: CORRUPTED_STATE,
  MISSING_EGO_ZAYIN: CORRUPTED_STATE,
  INVALID_EGO_TYPE: CORRUPTED_STATE,
  MISMATCHED_EGO: CORRUPTED_STATE,
  OUT_OF_RANGE_EGO_THREADSPIN: CORRUPTED_STATE,
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
  MISSING_REQUIRED_FIELD: CORRUPTED_STATE,
  UNKNOWN_FIELD: CORRUPTED_STATE,
  UNKNOWN_STARTGIFT_KEYWORD: CORRUPTED_STATE,
  MISMATCHED_STARTGIFT: CORRUPTED_STATE,
  DUPLICATE_SINNER: CORRUPTED_STATE,
  DUPLICATE_DEPLOYMENT: CORRUPTED_STATE,
  EXCESS_DEPLOYMENT: CORRUPTED_STATE,
  SIZE_EXCEEDED: 'pages.plannerMD.validation.noteTooLarge',
  MISSING_EGO_THREADSPIN_CEILING: MISSING_GAME_DATA,
  UNKNOWN_VERSION: MISSING_GAME_DATA,
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

export function plannerValidationError(friendly: FriendlyError) {
  return validationAppError({
    key: `planner:${friendly.key}`,
    ...(friendly.params !== undefined && { params: friendly.params }),
  })
}

export function toUserFriendlyError(error: PlannerValidationError): FriendlyError {
  const key = validationMessageKey(error.code)
  return 'params' in error ? { key, params: error.params } : { key }
}
