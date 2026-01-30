import type { z } from 'zod'
import type { UserForModSchema, ModerationActionSchema } from '@/schemas/ModeratorSchemas'

export type UserForMod = z.infer<typeof UserForModSchema>
export type ModerationAction = z.infer<typeof ModerationActionSchema>
