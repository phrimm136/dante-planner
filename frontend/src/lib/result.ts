/**
 * A two-case outcome for boundaries that must report why they failed.
 *
 * `null` cannot distinguish "there is nothing" from "the lookup broke", so a
 * caller that treats absence as a no-op silently treats failure as one too.
 */

export type Result<T, E> = { ok: true; value: T } | { ok: false; error: E }

/**
 * The member shape every discriminated error union in the app is built from.
 *
 * Unions stay per-boundary so each `switch` over one remains exhaustively
 * narrow; this only dedupes the shape of a member, it never merges the unions.
 */
export type Tagged<K extends string, P = object> = { kind: K } & P

/** Wrap a successful value. */
export function ok<T>(value: T): Result<T, never> {
  return { ok: true, value }
}

/** Wrap a failure. */
export function err<E>(error: E): Result<never, E> {
  return { ok: false, error }
}

/** A failure carried across a boundary that only understands throws. */
export class PipelineError<E> extends Error {
  readonly detail: E

  constructor(detail: E) {
    super('Pipeline step failed')
    this.name = 'PipelineError'
    this.detail = detail
  }
}

/** Unwrap a success, or throw the failure as a `PipelineError`. */
export function unwrapOrThrow<A, E>(r: Result<A, E>): A {
  if (r.ok) return r.value
  throw new PipelineError(r.error)
}
