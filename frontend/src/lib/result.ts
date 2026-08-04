/**
 * A two-case outcome for boundaries that must report why they failed.
 *
 * `null` cannot distinguish "there is nothing" from "the lookup broke", so a
 * caller that treats absence as a no-op silently treats failure as one too.
 */

export type Result<T, E> = { ok: true; value: T } | { ok: false; error: E }

/** Wrap a successful value. */
export function ok<T>(value: T): Result<T, never> {
  return { ok: true, value }
}

/** Wrap a failure. */
export function err<E>(error: E): Result<never, E> {
  return { ok: false, error }
}
