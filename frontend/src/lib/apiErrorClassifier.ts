/**
 * One vocabulary for "a request failed", shared by every consumer of the API.
 *
 * Pure: every reaction a failure deserves — cache invalidation, logging, toasts —
 * belongs to the caller that knows which surface is failing.
 */

import {
  AuthTemporarilyUnavailableError,
  BackendUnavailableError,
  BannedError,
  ConflictError,
  ForbiddenError,
  NotFoundError,
  RateLimitError,
  RetryableUnavailableError,
  ServiceUpdatingError,
  TimedOutError,
  ValidationError,
  WriteTemporarilyUnavailableError,
} from './api'

type Tagged<K extends string, P = unknown> = { kind: K } & P

/** Which account restriction blocked the request. */
export type RestrictionKind = 'banned' | 'timedOut'

/** Which half of the service could not serve the request. */
export type UnavailableScope = 'service' | 'backend' | 'auth' | 'write'

/** Every failure the API layer can hand a consumer, as one closed union. */
export type AppError =
  | Tagged<'conflict', { code: string; serverVersion: number | null }>
  | Tagged<'validation', { key: string; params?: Record<string, string> }>
  | Tagged<'restricted', { reason: RestrictionKind }>
  | Tagged<'rateLimit'>
  | Tagged<'forbidden', { code: string }>
  | Tagged<'notFound'>
  | Tagged<'unavailable', { scope: UnavailableScope }>
  | Tagged<'retryable'>
  | Tagged<'quota'>
  | Tagged<'unknown'>

/**
 * The backend classifies a 400 under a code that has no translated copy, so the
 * API's validation failures reuse the generic message; only client-side rules,
 * which own their wording, carry a key of their own.
 */
const API_VALIDATION_KEY = 'common:errors.generic.message'

/** Carry a validator's i18n key and params into the app error vocabulary. */
export function validationAppError(friendly: {
  key: string
  params?: Record<string, string>
}): AppError {
  return { kind: 'validation', key: friendly.key, params: friendly.params }
}

/** Map a thrown value onto the app error vocabulary. */
export function classifyAppError(error: unknown): AppError {
  if (error instanceof ConflictError) {
    return { kind: 'conflict', code: error.code, serverVersion: error.serverVersion }
  }
  if (error instanceof ValidationError) {
    return { kind: 'validation', key: API_VALIDATION_KEY }
  }
  if (error instanceof BannedError) return { kind: 'restricted', reason: 'banned' }
  if (error instanceof TimedOutError) return { kind: 'restricted', reason: 'timedOut' }
  if (error instanceof RateLimitError) return { kind: 'rateLimit' }
  if (error instanceof ForbiddenError) return { kind: 'forbidden', code: error.code }
  if (error instanceof NotFoundError) return { kind: 'notFound' }
  if (error instanceof ServiceUpdatingError) return { kind: 'unavailable', scope: 'service' }
  if (error instanceof BackendUnavailableError) return { kind: 'unavailable', scope: 'backend' }
  if (error instanceof AuthTemporarilyUnavailableError) {
    return { kind: 'unavailable', scope: 'auth' }
  }
  if (error instanceof WriteTemporarilyUnavailableError) {
    return { kind: 'unavailable', scope: 'write' }
  }
  if (error instanceof RetryableUnavailableError) return { kind: 'retryable' }

  // IndexedDB reports an exhausted quota as the request's DOMException.
  if (error instanceof DOMException && error.name === 'QuotaExceededError') {
    return { kind: 'quota' }
  }

  return { kind: 'unknown' }
}
