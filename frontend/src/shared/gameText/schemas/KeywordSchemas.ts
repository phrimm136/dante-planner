import { z } from 'zod'
import { EGOIdSchema, IdentityIdSchema } from '@/shared/gameData'

export const BattleKeywordSpecSchema = z
  .object({
    iconId: z.string().nullable(),
    buffType: z.string(),
    identities: z.array(IdentityIdSchema),
    egos: z.array(EGOIdSchema),
    egoGifts: z.array(z.string()),
  })
  .strict()

export const BattleKeywordSpecListSchema = z.record(z.string(), BattleKeywordSpecSchema)

export const BattleKeywordNameListSchema = z.record(z.string(), z.string())

export type BattleKeywordSpec = z.infer<typeof BattleKeywordSpecSchema>
