import { z } from 'zod'

export const SseEnvelopeSchema = z.object({
  type: z.enum([
    'created',
    'updated',
    'deleted',
    'notify:comment',
    'notify:published',
    'notify:recommended',
    'settings:invalidated',
  ]),
  entityType: z.string().optional(),
  userId: z.number().optional(),
  plannerId: z.string().optional(),
  entityId: z.string().optional(),
  deletedId: z.string().nullable().optional(),
  payload: z.unknown().optional(),
})

export type SseEnvelope = z.infer<typeof SseEnvelopeSchema>
