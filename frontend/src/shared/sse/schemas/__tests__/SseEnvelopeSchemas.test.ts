import { describe, it, expect } from 'vitest'

import { SseAccountSuspendedSchema, SseEnvelopeSchema, SseEventTypeSchema } from '@/shared/sse'

/**
 * Transcribed from backend `SseEventType`: every constant's `@JsonValue`, in
 * declaration order.
 */
const BACKEND_SSE_EVENT_TYPES = [
  'created',
  'updated',
  'deleted',
  'comment:added',
  'notify:comment',
  'notify:published',
  'notify:recommended',
  'settings:invalidated',
  'account_suspended',
]

describe('SseEventTypeSchema', () => {
  it('names exactly the backend wire values', () => {
    const byValue = (a: string, b: string) => a.localeCompare(b)
    expect([...SseEventTypeSchema.options].sort(byValue)).toEqual(
      [...BACKEND_SSE_EVENT_TYPES].sort(byValue),
    )
  })
})

describe('SseEnvelopeSchema', () => {
  it('accepts a planner sync envelope', () => {
    const result = SseEnvelopeSchema.safeParse({
      type: 'updated',
      userId: 1,
      plannerId: 'pl',
      entityId: 'e1',
      payload: { id: 'p1' },
    })

    expect(result.success).toBe(true)
  })

  it('accepts a comment envelope', () => {
    const result = SseEnvelopeSchema.safeParse({
      type: 'comment:added',
      plannerId: 'pl',
      entityId: 'c1',
      payload: { id: 'c1' },
    })

    expect(result.success).toBe(true)
  })

  it('accepts a minimal envelope with only a type', () => {
    const result = SseEnvelopeSchema.safeParse({ type: 'notify:published' })

    expect(result.success).toBe(true)
  })

  it('rejects an unknown type', () => {
    const result = SseEnvelopeSchema.safeParse({ type: 'bogus' })

    expect(result.success).toBe(false)
  })
})

describe('SseAccountSuspendedSchema', () => {
  it.each(['BAN', 'TIMEOUT'])('accepts the %s payload the backend emits', (suspensionType) => {
    const result = SseAccountSuspendedSchema.safeParse({
      suspensionType,
      reason: '',
      durationMinutes: 0,
    })

    expect(result.success).toBe(true)
  })

  it('rejects a suspension type the backend never emits', () => {
    const result = SseAccountSuspendedSchema.safeParse({
      suspensionType: 'TIMED_OUT',
      reason: '',
      durationMinutes: 0,
    })

    expect(result.success).toBe(false)
  })
})
