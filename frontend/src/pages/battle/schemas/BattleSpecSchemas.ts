import { z } from 'zod'
import { BATTLE_CASES } from '../lib/battleVocabulary'

export const BATTLE_SPEC_SHAPE = {
  cases: z.array(z.enum(BATTLE_CASES)),
  chapters: z.array(z.string()),
  themePacks: z.array(z.string()),
  events: z.array(z.string()),
  hidden: z.boolean(),
  railwayLines: z.array(z.number().int()),
  luxcavations: z.array(z.string()),
  spriteId: z.string(),
}

export const BattleNameListSchema = z.record(z.string(), z.string())

export const BattleFacetNamesSchema = z
  .object({
    chapters: z.record(z.string(), z.string()),
    railwayLines: z.record(z.string(), z.string()),
    luxcavations: z.record(z.string(), z.string()),
    events: z.record(z.string(), z.string()),
  })
  .strict()

export type BattleNameList = z.infer<typeof BattleNameListSchema>
export type BattleFacetNames = z.infer<typeof BattleFacetNamesSchema>
