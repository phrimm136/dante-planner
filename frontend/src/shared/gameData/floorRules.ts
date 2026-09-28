import plannerFloorRulesJson from '@static/data/plannerFloorRules.json'
import { z } from 'zod'
import { DUNGEON_IDX, MD_CATEGORIES } from './constants'
import type { DungeonIdx, MDCategory } from './constants'

export const FLOOR_RULE_STAGES = ['draft', 'publish', 'index'] as const

export type FloorRuleStage = (typeof FLOOR_RULE_STAGES)[number]

export const FLOOR_RULE_NAMES = [
  'everyFloorPresent',
  'themePackPresent',
  'notRepeated',
  'sequence',
  'difficultyInRange',
  'noNormalAfterHard',
  'giftUnique',
] as const

export type FloorRuleName = (typeof FLOOR_RULE_NAMES)[number]

const isUnique = (values: readonly unknown[]) => new Set(values).size === values.length

const StageListSchema = z
  .array(z.enum(FLOOR_RULE_STAGES))
  .refine(isUnique, { message: 'stages must not repeat' })
  .readonly()

const DifficultySetSchema = z
  .array(z.literal(Object.values(DUNGEON_IDX)))
  .min(1)
  .refine(isUnique, { message: 'difficulties must not repeat' })
  .readonly()

const CategoryFloorsSchema = z
  .object({
    floorCount: z.number().int().positive(),
    difficulties: z.array(DifficultySetSchema).readonly(),
  })
  .strict()
  .refine((category) => category.difficulties.length === category.floorCount, {
    message: 'difficulties must hold one set per floor',
  })

export const FloorRuleTableSchema = z
  .object({
    schemaVersion: z.number().int().positive(),
    stages: StageListSchema.refine((stages) => stages.length > 0, {
      message: 'stages must not be empty',
    }),
    categories: z.record(z.enum(MD_CATEGORIES), CategoryFloorsSchema),
    rules: z
      .object(
        Object.fromEntries(FLOOR_RULE_NAMES.map((rule) => [rule, StageListSchema])) as Record<
          FloorRuleName,
          typeof StageListSchema
        >,
      )
      .strict(),
  })
  .strict()
  .superRefine((table, ctx) => {
    for (const rule of FLOOR_RULE_NAMES) {
      for (const stage of table.rules[rule]) {
        if (!table.stages.includes(stage)) {
          ctx.addIssue({
            code: 'custom',
            path: ['rules', rule],
            message: `rule ${rule} names stage ${stage}, which stages does not list`,
          })
        }
      }
    }
  })

export type FloorRuleTable = z.infer<typeof FloorRuleTableSchema>

export const FLOOR_RULE_TABLE: FloorRuleTable = FloorRuleTableSchema.parse(plannerFloorRulesJson)

export function floorCount(category: MDCategory): number {
  return FLOOR_RULE_TABLE.categories[category].floorCount
}

export function maxFloorCount(): number {
  return Math.max(...Object.values(FLOOR_RULE_TABLE.categories).map((floors) => floors.floorCount))
}

export function allowedDifficulties(
  category: MDCategory,
  floorIndex: number,
): readonly DungeonIdx[] | undefined {
  return FLOOR_RULE_TABLE.categories[category].difficulties[floorIndex]
}

export function stagesFor(rule: FloorRuleName): readonly FloorRuleStage[] {
  return FLOOR_RULE_TABLE.rules[rule]
}
