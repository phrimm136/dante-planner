import { z } from 'zod'
import { AssistantIdSchema } from '@/shared/gameData'
import { BATTLE_SPEC_SHAPE, BattleNameListSchema } from './BattleSpecSchemas'

export const AssistantSpecSchema = z.object(BATTLE_SPEC_SHAPE).strict()

export const AssistantSpecListSchema = z.record(AssistantIdSchema, AssistantSpecSchema)

export const AssistantNameListSchema = BattleNameListSchema

export type AssistantSpec = z.infer<typeof AssistantSpecSchema>
export type AssistantSpecList = z.infer<typeof AssistantSpecListSchema>
export type AssistantNameList = z.infer<typeof AssistantNameListSchema>
