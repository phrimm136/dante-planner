import { describe, it, expect } from 'vitest'
import { ok, err, unwrapOrThrow, PipelineError } from '../result'

const NOT_THROWN = Symbol('not thrown')

function capture(run: () => unknown): unknown {
  try {
    run()
  } catch (thrown) {
    return thrown
  }
  return NOT_THROWN
}

const VALUES: [string, unknown][] = [
  ['a string', 'planner'],
  ['zero', 0],
  ['false', false],
  ['null', null],
  ['undefined', undefined],
  ['an object', { id: 1 }],
  ['an array', [1, 2, 3]],
]

const ERRORS: [string, unknown][] = [
  ['a string code', 'NOT_FOUND'],
  ['a tagged object', { kind: 'validation', field: 'name' }],
  ['an Error instance', new Error('boom')],
  ['zero', 0],
  ['null', null],
]

describe('unwrapOrThrow', () => {
  it.each(VALUES)('returns the value on ok for %s', (_label, value) => {
    expect(unwrapOrThrow(ok(value))).toBe(value)
  })

  it.each(ERRORS)('throws a PipelineError on err for %s', (_label, error) => {
    expect(() => unwrapOrThrow(err(error))).toThrow(PipelineError)
  })

  it.each(ERRORS)('carries the exact error object as detail for %s', (_label, error) => {
    const thrown = capture(() => unwrapOrThrow(err(error)))

    expect(thrown).toBeInstanceOf(PipelineError)
    expect((thrown as PipelineError<unknown>).detail).toBe(error)
  })

  it('survives the throw/catch round-trip as a named Error subclass', () => {
    const detail = { kind: 'conflict' } as const
    const thrown = capture(() => unwrapOrThrow(err(detail)))

    expect(thrown).toBeInstanceOf(Error)
    expect(thrown instanceof PipelineError).toBe(true)
    expect((thrown as PipelineError<typeof detail>).name).toBe('PipelineError')
  })

  it('re-narrows a caught error back to its typed detail', () => {
    const detail = { kind: 'timeout' } as const
    const thrown = capture((): string => unwrapOrThrow(err(detail)))

    expect(thrown instanceof PipelineError).toBe(true)
    expect((thrown as PipelineError<typeof detail>).detail.kind).toBe('timeout')
  })
})
