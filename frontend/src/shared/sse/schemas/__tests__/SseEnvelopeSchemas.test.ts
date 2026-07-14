import { describe, it, expect } from 'vitest'

import { SseEnvelopeSchema } from '@/shared/sse'

describe('SseEnvelopeSchema', () => {
  it('accepts a full Phase-10 envelope', () => {
    const result = SseEnvelopeSchema.safeParse({
      type: 'updated',
      entityType: 'planner',
      userId: 1,
      plannerId: 'pl',
      entityId: 'e1',
      deletedId: null,
      payload: { id: 'p1' },
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
