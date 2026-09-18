import { z } from 'zod'
import { EXTRACTION_RATES } from '../lib/extractionRates'

export const ExtractionTargetTypeSchema = z.enum(['threeStarId', 'ego', 'announcer'])

export const ActiveRateTableSchema = z.enum([
  'standard',
  'withAnnouncer',
  'allEgoCollected',
  'allEgoWithAnnouncer',
])

export const BannerModifiersSchema = z
  .object({
    allEgoCollected: z.boolean(),
    hasAnnouncer: z.boolean(),
  })
  .strict()

export const ExtractionTargetSchema = z
  .object({
    type: ExtractionTargetTypeSchema,
    wantedCopies: z.number().int().min(1),
    currentCopies: z.number().int().min(0),
  })
  .strict()
  .refine((data) => data.currentCopies < data.wantedCopies, {
    message: 'currentCopies must be less than wantedCopies',
  })

export const ExtractionInputSchema = z
  .object({
    plannedPulls: z.number().int().min(0).max(10000),
    featuredThreeStarCount: z.number().int().min(0).max(20),
    featuredEgoCount: z.number().int().min(0).max(20),
    featuredAnnouncerCount: z.number().int().min(0).max(20),
    modifiers: BannerModifiersSchema,
    targets: z.array(ExtractionTargetSchema),
    currentPity: z
      .number()
      .int()
      .min(0)
      .max(EXTRACTION_RATES.PITY_PULLS - 1),
  })
  .strict()
  .refine(
    (data) => {
      if (data.modifiers.allEgoCollected && data.featuredEgoCount > 0) {
        return false
      }
      return true
    },
    { message: 'featuredEgoCount must be 0 when allEgoCollected is true' },
  )
  .refine(
    (data) => {
      const hasEgoTarget = data.targets.some((t) => t.type === 'ego')
      if (hasEgoTarget && data.featuredEgoCount === 0) {
        return false
      }
      return true
    },
    { message: 'Cannot have EGO target when featuredEgoCount is 0' },
  )
  .refine(
    (data) => {
      const hasAnnouncerTarget = data.targets.some((t) => t.type === 'announcer')
      if (hasAnnouncerTarget && !data.modifiers.hasAnnouncer) {
        return false
      }
      return true
    },
    { message: 'Cannot have Announcer target when banner has no Announcer' },
  )
  .refine(
    (data) => {
      const hasThreeStarTarget = data.targets.some((t) => t.type === 'threeStarId')
      if (hasThreeStarTarget && data.featuredThreeStarCount === 0) {
        return false
      }
      return true
    },
    { message: 'Cannot have a 3-star target when featuredThreeStarCount is 0' },
  )
  .refine(
    (data) => {
      const hasAnnouncerTarget = data.targets.some((t) => t.type === 'announcer')
      if (hasAnnouncerTarget && data.featuredAnnouncerCount === 0) {
        return false
      }
      return true
    },
    { message: 'Cannot have an Announcer target when featuredAnnouncerCount is 0' },
  )

export const TargetProbabilitySchema = z
  .object({
    target: ExtractionTargetSchema,
    probability: z.number().min(0).max(1),
    expectedPulls: z.number().min(0),
    pityApplies: z.boolean(),
  })
  .strict()

export const SuccessiveProbabilitySchema = z
  .object({
    count: z.number().int().min(0),
    probability: z.number().min(0).max(1),
  })
  .strict()

export const ExtractionResultSchema = z
  .object({
    targetResults: z.array(TargetProbabilitySchema),
    anyTargetProbability: z.number().min(0).max(1),
    allTargetProbability: z.number().min(0).max(1),
    successiveProbabilities: z.array(SuccessiveProbabilitySchema),
    totalItemsWanted: z.number().int().min(0),
    pityCount: z.number().int().min(0),
    lunacyCost: z.number().int().min(0),
    pullsUntilPity: z.number().int().min(0).max(EXTRACTION_RATES.PITY_PULLS),
    activeRateTable: ActiveRateTableSchema,
  })
  .strict()

export const EffectiveRatesSchema = z
  .object({
    threeStarIdEach: z.number().min(0).max(1),
    egoEach: z.number().min(0).max(1),
    announcer: z.number().min(0).max(1),
    threeStarIdTotal: z.number().min(0).max(1),
    egoTotal: z.number().min(0).max(1),
  })
  .strict()
