export type Result<T, E> = { ok: true; value: T } | { ok: false; error: E }

export type Tagged<K extends string, P = object> = { kind: K } & P

export function ok<T>(value: T): Result<T, never> {
  return { ok: true, value }
}

export function err<E>(error: E): Result<never, E> {
  return { ok: false, error }
}

export class PipelineError<E> extends Error {
  readonly detail: E

  constructor(detail: E) {
    super('Pipeline step failed')
    this.name = 'PipelineError'
    this.detail = detail
  }
}

export function unwrapOrThrow<A, E>(r: Result<A, E>): A {
  if (r.ok) return r.value
  throw new PipelineError(r.error)
}
