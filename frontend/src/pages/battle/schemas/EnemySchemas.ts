import { z } from 'zod'
import { EnemyIdSchema } from '@/shared/gameData'
import { BATTLE_SPEC_SHAPE, BattleNameListSchema } from './BattleSpecSchemas'

export const EnemySpecSchema = z
  .object({ ...BATTLE_SPEC_SHAPE, personalityId: z.number().optional() })
  .strict()

export const EnemySpecListSchema = z.record(EnemyIdSchema, EnemySpecSchema)

export const EnemyNameListSchema = BattleNameListSchema

export type EnemySpec = z.infer<typeof EnemySpecSchema>
export type EnemySpecList = z.infer<typeof EnemySpecListSchema>
export type EnemyNameList = z.infer<typeof EnemyNameListSchema>
