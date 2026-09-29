import fs from 'node:fs'
import path from 'node:path'
import { describe, it, expect } from 'vitest'

import { SseAccountSuspendedSchema, SseEnvelopeSchema, SseEventTypeSchema } from '@/shared/sse'

const FRONTEND_ROOT = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  '../../../../..',
)
const EVENT_TYPES_PATH = path.resolve(FRONTEND_ROOT, '../testdata/sse-event-types.json')

/**
 * Every backend `SseEventType` constant's `@JsonValue`, read from
 * `testdata/sse-event-types.json`, which a backend test keeps equal to the enum.
 * Compared as a set, so the file's order carries no meaning here.
 * `connected` is absent — `AbstractSseService` emits it as a literal, so it is
 * a transport handshake rather than a backend event constant.
 */
const BACKEND_SSE_EVENT_TYPES = JSON.parse(fs.readFileSync(EVENT_TYPES_PATH, 'utf-8')) as string[]

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
