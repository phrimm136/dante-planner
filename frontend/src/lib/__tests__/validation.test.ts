import { describe, it, expect } from 'vitest'
import { z } from 'zod'

import { validateData } from '../validation'

const TestSchema = z.object({
  id: z.string(),
  level: z.number(),
})

describe('validateData', () => {
  it('returns the parsed data on success', () => {
    const input = { id: 'yisang-01', level: 45 }

    const result = validateData(input, TestSchema, 'test entity')

    expect(result).toEqual({ id: 'yisang-01', level: 45 })
  })

  it('throws with the exact "[context] Validation failed: …" message on failure', () => {
    const invalid = { id: 42, level: 'not-a-number' }
    const parsed = TestSchema.safeParse(invalid)
    if (parsed.success) throw new Error('fixture must fail schema validation')

    expect(() => validateData(invalid, TestSchema, 'test entity')).toThrowError(
      new Error(`[test entity] Validation failed: ${parsed.error.message}`),
    )
  })

  it('interpolates the given context into the message prefix', () => {
    expect(() => validateData(null, TestSchema, 'identity specList')).toThrowError(
      /^\[identity specList\] Validation failed: /,
    )
  })

  it('strips undeclared fields (z.object default behavior)', () => {
    const input = { id: 'yisang-01', level: 45, extra: 'stripped' }

    const result = validateData(input, TestSchema, 'test entity')

    expect(result).toEqual({ id: 'yisang-01', level: 45 })
    expect('extra' in result).toBe(false)
  })
})
