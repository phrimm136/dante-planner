/**
 * Planner save error classification and messaging.
 *
 * One vocabulary for "a save went wrong": the hook stores a `SaveError`, the
 * editor renders it, and imperative call sites that never build one (publish
 * toggle, mirror upgrade) reuse the same restriction detection.
 */

import i18n from '@/lib/i18n'
import { toast } from '@/lib/toast'
import { assertNever } from '@/lib/utils'
import {
  BannedError,
  ConflictError,
  TimedOutError,
  WriteTemporarilyUnavailableError,
} from '@/lib/api'
import { queryClient } from '@/lib/queryClient'
import { authQueryKeys } from '@/shared/auth'

import type { ConflictState } from '../types/PlannerTypes'

/** Which account restriction blocked the write. */
export type RestrictionKind = 'banned' | 'timedOut'

/**
 * Why a save failed, in the terms the UI reacts to.
 *
 * - `conflict` opens the resolution dialog rather than toasting.
 * - `syncPaused` is a degradation, not a failure: the local write succeeded.
 */
export type SaveError =
  | { kind: 'conflict'; state: ConflictState }
  | { kind: 'validation'; key: string; params?: Record<string, string> }
  | { kind: 'quota' }
  | { kind: 'moderation'; reason: RestrictionKind }
  | { kind: 'syncPaused' }
  | { kind: 'unknown' }

const MODERATION_TOAST_KEY: Record<RestrictionKind, string> = {
  banned: 'moderation.banned',
  timedOut: 'moderation.timedOut',
}

const QUOTA_TOAST_KEY = 'pages.plannerMD.save.quotaExceeded'
const QUOTA_TOAST_FALLBACK = 'Storage quota exceeded'

/** Error `code` carried by the ad-hoc errors thrown inside the save pipeline. */
export const USER_FRIENDLY_VALIDATION_CODE = 'userFriendlyValidation'
const VALIDATION_FAILED_CODE = 'validationFailed'
const QUOTA_EXCEEDED_CODE = 'quotaExceeded'
const CLASSIFIED_CODE = 'classifiedSaveError'

interface CodedError extends Error {
  code?: string
  i18nKey?: string
  i18nParams?: Record<string, string>
  saveError?: SaveError
}

/**
 * Build the error the save pipeline throws to abort on a user-facing validation
 * failure, carrying the i18n key/params through the shared `catch`.
 */
export function userFriendlyValidationError(friendly: {
  key: string
  params?: Record<string, string>
}): CodedError {
  return Object.assign(new Error(friendly.key), {
    code: USER_FRIENDLY_VALIDATION_CODE,
    i18nKey: friendly.key,
    i18nParams: friendly.params,
  })
}

/**
 * Build the error a save step throws once it already knows which {@link SaveError}
 * the failure is, so the shared `catch` reports that rather than re-deriving
 * `unknown` from a wrapper.
 */
export function classifiedSaveError(saveError: SaveError): CodedError {
  return Object.assign(new Error(saveError.kind), {
    code: CLASSIFIED_CODE,
    saveError,
  })
}

/** The `SaveError` an error carries verbatim, or null when it carries none. */
function carriedSaveError(error: unknown): SaveError | null {
  if (!(error instanceof Error)) return null
  const coded = error as CodedError
  return coded.code === CLASSIFIED_CODE ? (coded.saveError ?? null) : null
}

/** The account restriction an error reports, or null when it reports none. */
export function restrictionOf(error: unknown): RestrictionKind | null {
  if (error instanceof BannedError) return 'banned'
  if (error instanceof TimedOutError) return 'timedOut'
  return null
}

/**
 * Map a thrown value onto the save error vocabulary.
 *
 * A restriction invalidates the cached account so the app-wide banner appears.
 */
export function classifySaveError(error: unknown): SaveError {
  const carried = carriedSaveError(error)
  if (carried) return carried

  if (error instanceof ConflictError) {
    return {
      kind: 'conflict',
      state: { serverVersion: error.serverVersion, detectedAt: new Date().toISOString() },
    }
  }

  const restriction = restrictionOf(error)
  if (restriction) {
    console.warn(`Save blocked: user is ${restriction}`)
    void queryClient.invalidateQueries({ queryKey: authQueryKeys.me })
    return { kind: 'moderation', reason: restriction }
  }

  if (error instanceof WriteTemporarilyUnavailableError) {
    console.warn('Write temporarily unavailable — saved locally, sync paused')
    return { kind: 'syncPaused' }
  }

  if (error instanceof Error) {
    const coded = error as CodedError

    if (
      coded.code === QUOTA_EXCEEDED_CODE ||
      error.message.includes('QuotaExceeded') ||
      error.message.includes('quota')
    ) {
      console.error('Save failed (quota exceeded):', error.message)
      return { kind: 'quota' }
    }

    if (coded.code === USER_FRIENDLY_VALIDATION_CODE && coded.i18nKey) {
      console.error('Save failed (user validation):', error.message)
      return { kind: 'validation', key: coded.i18nKey, params: coded.i18nParams }
    }

    if (coded.code === VALIDATION_FAILED_CODE) {
      console.error('Save failed (validation):', error.message)
      return { kind: 'unknown' }
    }
  }

  console.error('Save failed:', error)
  return { kind: 'unknown' }
}

/**
 * The toast text for a save error, or null when the error is surfaced some
 * other way (conflict dialog) or deliberately silent (sync paused).
 */
export function saveErrorMessage(error: SaveError, fallbackKey: string): string | null {
  switch (error.kind) {
    case 'conflict':
    case 'syncPaused':
      return null
    case 'moderation':
      return i18n.t(MODERATION_TOAST_KEY[error.reason], { ns: 'common' })
    case 'quota':
      return i18n.t(QUOTA_TOAST_KEY, { ns: 'planner', defaultValue: QUOTA_TOAST_FALLBACK })
    case 'validation':
      return i18n.t(error.key, { ns: 'planner', ...error.params })
    case 'unknown':
      return i18n.t(fallbackKey, { ns: 'planner' })
    default:
      return assertNever(error)
  }
}

/**
 * Toast a thrown value from an imperative planner write.
 *
 * Restrictions get their own message; anything else — including errors this
 * call site has no separate handling for — falls back to `fallbackKey`.
 */
export function toastForError(error: unknown, fallbackKey: string): void {
  const restriction = restrictionOf(error)
  toast.error(
    restriction
      ? i18n.t(MODERATION_TOAST_KEY[restriction], { ns: 'common' })
      : i18n.t(fallbackKey, { ns: 'planner' }),
  )
}
