import { z } from 'zod'

/**
 * Every event type the stream can name, mirroring the backend `SseEventType`
 * wire values.
 */
export const SseEventTypeSchema = z.enum([
  'created',
  'updated',
  'deleted',
  'comment:added',
  'notify:comment',
  'notify:published',
  'notify:recommended',
  'settings:invalidated',
  'account_suspended',
])

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

export type SseEventType = z.infer<typeof SseEventTypeSchema>
export type SseEnvelope = z.infer<typeof SseEnvelopeSchema>
export type SseAccountSuspended = z.infer<typeof SseAccountSuspendedSchema>
