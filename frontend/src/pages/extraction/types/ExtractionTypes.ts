import { z } from 'zod'
import {
  ExtractionTargetTypeSchema,
  BannerModifiersSchema,
  ExtractionTargetSchema,
  ExtractionInputSchema,
  TargetProbabilitySchema,
  SuccessiveProbabilitySchema,
  ExtractionResultSchema,
  EffectiveRatesSchema,
} from '../schemas/ExtractionSchemas'

/**
 * Extraction (Gacha) Types
 *
 * Type definitions for extraction probability calculator.
 * Used to track user inputs and calculation results.
 *
 * @see ExtractionSchemas.ts for Zod validation
 * @see lib/extractionCalculator.ts for probability math
 */

/**
 * Target types available for extraction
 * - threeStarId: 3-star Identity (rate-up)
 * - ego: EGO (rate-up)
 * - announcer: Announcer skin (rate-up, when featured)
 */
export type ExtractionTargetType = z.infer<typeof ExtractionTargetTypeSchema>

/**
 * Banner modifier flags that affect rates
 */
export type BannerModifiers = z.infer<typeof BannerModifiersSchema>

/**
 * Target configuration for a single extraction target
 * Represents a featured item the user wants to obtain
 */
export type ExtractionTarget = z.infer<typeof ExtractionTargetSchema>

/**
 * User input configuration for extraction calculator
 * All fields needed to compute probability
 */
export type ExtractionInput = z.infer<typeof ExtractionInputSchema>

/**
 * Probability result for a single target
 */
export type TargetProbability = z.infer<typeof TargetProbabilitySchema>

/**
 * Successive probability entry (P(at least k items))
 */
export type SuccessiveProbability = z.infer<typeof SuccessiveProbabilitySchema>

/**
 * Complete calculation results
 */
export type ExtractionResult = z.infer<typeof ExtractionResultSchema>

/**
 * Effective rates after applying modifiers and splitting among featured items
 */
export type EffectiveRates = z.infer<typeof EffectiveRatesSchema>
