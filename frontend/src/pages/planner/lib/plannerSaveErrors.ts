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

/** Carry a validator's i18n key and params into the save error vocabulary. */
export function validationSaveError(friendly: {
  key: string
  params?: Record<string, string>
}): SaveError {
  return { kind: 'validation', key: friendly.key, params: friendly.params }
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
 * Pure: every reaction a failure deserves — cache invalidation, logging, toasts —
 * belongs to the caller that knows which surface is failing.
 */
export function classifySaveError(error: unknown): SaveError {
  if (error instanceof ConflictError) {
    return {
      kind: 'conflict',
      state: { serverVersion: error.serverVersion, detectedAt: new Date().toISOString() },
    }
  }

  const restriction = restrictionOf(error)
  if (restriction) return { kind: 'moderation', reason: restriction }

  if (error instanceof WriteTemporarilyUnavailableError) return { kind: 'syncPaused' }

  // IndexedDB reports an exhausted quota as the request's DOMException.
  if (error instanceof DOMException && error.name === 'QuotaExceededError') {
    return { kind: 'quota' }
  }

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
