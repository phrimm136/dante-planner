import { z } from 'zod'
import {
  ExtractionTargetTypeSchema,
  BannerModifiersSchema,
  ExtractionTargetSchema,
  ExtractionInputSchema,
  TargetProbabilitySchema,
  ExtractionResultSchema,
  EffectiveRatesSchema,
} from '../schemas/ExtractionSchemas'

export type ExtractionTargetType = z.infer<typeof ExtractionTargetTypeSchema>

export type BannerModifiers = z.infer<typeof BannerModifiersSchema>

export type ExtractionTarget = z.infer<typeof ExtractionTargetSchema>

export type ExtractionInput = z.infer<typeof ExtractionInputSchema>

export type TargetProbability = z.infer<typeof TargetProbabilitySchema>

export type ExtractionResult = z.infer<typeof ExtractionResultSchema>

export type EffectiveRates = z.infer<typeof EffectiveRatesSchema>
