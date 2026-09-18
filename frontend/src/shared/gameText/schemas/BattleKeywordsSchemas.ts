import { z } from 'zod'

export const BattleKeywordEntrySchema = z
  .object({
    name: z.string(),
    desc: z.string(),
    flavor: z.string().optional(),
  })
  .strict()

export const BattleKeywordsSchema = z.record(z.string(), BattleKeywordEntrySchema)

export type BattleKeywordI18nEntry = z.infer<typeof BattleKeywordEntrySchema>
