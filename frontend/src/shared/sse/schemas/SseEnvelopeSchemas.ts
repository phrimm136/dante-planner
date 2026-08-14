import { z } from 'zod'

import { SSE_EVENTS, type SseEventType } from '@/lib/constants'

/** Derived from SSE_EVENTS so the enum cannot drift from the transport's vocabulary. */
export const SseEventTypeSchema = z.enum(
  Object.values(SSE_EVENTS) as [SseEventType, ...SseEventType[]],
)

/**
 * The routing shell around a server event, for the types delivered as an
 * envelope rather than as their bare payload.
 */
export const SseEnvelopeSchema = z.object({
  type: SseEventTypeSchema,
  userId: z.number().optional(),
  plannerId: z.string().optional(),
  entityId: z.string().optional(),
  payload: z.unknown().optional(),
})

/**
 * The bare payload of an `account_suspended` event.
 */
export const SseAccountSuspendedSchema = z.object({
  suspensionType: z.enum(['BAN', 'TIMEOUT']),
  reason: z.string(),
  durationMinutes: z.number().int(),
})

export type { SseEventType }
export type SseEnvelope = z.infer<typeof SseEnvelopeSchema>
export type SseAccountSuspended = z.infer<typeof SseAccountSuspendedSchema>
