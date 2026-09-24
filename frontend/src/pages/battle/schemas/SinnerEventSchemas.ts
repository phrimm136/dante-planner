import { z } from 'zod'
import { SinnerEventIdSchema } from '@/shared/gameData'
import { BATTLE_SPEC_SHAPE, BattleNameListSchema } from './BattleSpecSchemas'

export const SinnerEventSpecSchema = z.object(BATTLE_SPEC_SHAPE).strict()

export const SinnerEventSpecListSchema = z.record(SinnerEventIdSchema, SinnerEventSpecSchema)

export const SinnerEventNameListSchema = BattleNameListSchema

export type SinnerEventSpec = z.infer<typeof SinnerEventSpecSchema>
export type SinnerEventSpecList = z.infer<typeof SinnerEventSpecListSchema>
export type SinnerEventNameList = z.infer<typeof SinnerEventNameListSchema>
