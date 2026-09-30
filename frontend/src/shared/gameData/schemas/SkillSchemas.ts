import { z } from 'zod'

import { SkillIdSchema } from '../ids'
import { AbPartTypeSchema } from './SharedSchemas'

export const SkillDescEntrySchema = z.object({
  desc: z.string().optional(),
  coinDescs: z.array(z.string()).optional(),
})

export type SkillDescEntry = z.infer<typeof SkillDescEntrySchema>

export type Uptie = 1 | 2 | 3 | 4

export type Threadspin = 1 | 2 | 3 | 4 | 5

export const SkillCoinSchema = z
  .object({
    scale: z.number(),
    operator: z.string(),
    grade: z.number(),
    color: z.string(),
    special: z.boolean(),
  })
  .strict()

export const SkillLevelSchema = z
  .object({
    level: z.number(),
    defType: z.string(),
    attributeType: z.string(),
    atkType: z.string(),
    iconID: z.string().optional(),
    defaultValue: z.number(),
    targetNum: z.number(),
    skillLevelCorrection: z.number(),
    coins: z.array(SkillCoinSchema),
    coinString: z.string(),
  })
  .strict()

export const SkillEntrySchema = z
  .object({
    skillTier: z.number(),
    skillType: z.string(),
    importanceLevel: z.number(),
    mpUsage: z.number(),
    isArea: z.boolean(),
    requireIDList: z.array(z.string()).optional(),
    linkedPartTypes: z.array(AbPartTypeSchema).optional(),
    levels: z.tuple([SkillLevelSchema], SkillLevelSchema),
  })
  .strict()

export const SkillMapSchema = z.record(SkillIdSchema, SkillEntrySchema)

export function skillMapWithRetiredKeys<const K extends string>(retiredKeys: readonly K[]) {
  const retired = Object.fromEntries(
    retiredKeys.map((key) => [key, z.array(z.unknown()).optional()]),
  ) as Record<K, z.ZodOptional<z.ZodArray<z.ZodUnknown>>>
  return z
    .object(retired)
    .catchall(z.unknown())
    .transform((skills) => {
      const map: Record<string, unknown> = { ...skills }
      for (const key of retiredKeys) delete map[key]
      return map
    })
    .pipe(SkillMapSchema)
}

export type SkillCoin = z.infer<typeof SkillCoinSchema>
export type SkillLevel = z.infer<typeof SkillLevelSchema>
export type SkillEntry = z.infer<typeof SkillEntrySchema>
export type SkillMap = z.infer<typeof SkillMapSchema>
