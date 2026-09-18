import { z } from 'zod'

export const BuffReferenceDataSchema = z
  .object({
    activeRound: z.number().optional(),
    buffKeyword: z.string().optional(),
    stack: z.number().optional(),
    turn: z.number().optional(),
    limit: z.number().optional(),
  })
  .strict()

export const BuffEffectSchema = z
  .object({
    type: z.string(),
    value: z.number().optional(),
    value2: z.number().optional(),
    isTypoExist: z.boolean(),
    customLocalizeTextId: z.string().optional(),
    referenceData: BuffReferenceDataSchema.optional(),
  })
  .strict()

export const BuffUIConfigSchema = z
  .object({
    iconSpriteId: z.string(),
  })
  .strict()

export const StartBuffDataSchema = z
  .object({
    level: z.number(),
    baseId: z.coerce.number(),
    cost: z.number(),
    localizeId: z.string(),
    effects: z.array(BuffEffectSchema),
    uiConfig: BuffUIConfigSchema,
  })
  .strict()

export const StartBuffDataListSchema = z.record(z.string(), StartBuffDataSchema)

export const StartBuffI18nSchema = z.record(z.string(), z.string())

export type BuffReferenceData = z.infer<typeof BuffReferenceDataSchema>
export type BuffEffect = z.infer<typeof BuffEffectSchema>
export type BuffUIConfig = z.infer<typeof BuffUIConfigSchema>
export type StartBuffData = z.infer<typeof StartBuffDataSchema>
export type StartBuffDataList = z.infer<typeof StartBuffDataListSchema>
export type StartBuffI18n = z.infer<typeof StartBuffI18nSchema>
