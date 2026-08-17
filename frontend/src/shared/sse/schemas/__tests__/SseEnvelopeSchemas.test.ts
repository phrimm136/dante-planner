import { describe, it, expect } from 'vitest'

import { SseAccountSuspendedSchema, SseEnvelopeSchema, SseEventTypeSchema } from '@/shared/sse'

/**
 * Transcribed from backend `SseEventType`: every constant's `@JsonValue`.
 * Compared as a set, so the order written here carries no meaning.
 * `connected` is absent — `AbstractSseService` emits it as a literal, so it is
 * a transport handshake rather than a backend event constant.
 */
const BACKEND_SSE_EVENT_TYPES = [
  'comment:added',
  'notify:comment',
  'notify:published',
  'notify:recommended',
  'settings:invalidated',
  'account_suspended',
]

/** Spelled out rather than read back from the source this test is checking. */
const CONNECTED_HANDSHAKE = 'connected'

const byValue = (a: string, b: string) => a.localeCompare(b)

describe('SseEventTypeSchema', () => {
  it('carries the backend wire values plus the transport handshake', () => {
    expect([...SseEventTypeSchema.options].sort(byValue)).toEqual(
      [...BACKEND_SSE_EVENT_TYPES, CONNECTED_HANDSHAKE].sort(byValue),
    )
  })
})

describe('SseEnvelopeSchema', () => {
  it('accepts an envelope carrying every optional field', () => {
    const result = SseEnvelopeSchema.safeParse({
      type: 'settings:invalidated',
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
