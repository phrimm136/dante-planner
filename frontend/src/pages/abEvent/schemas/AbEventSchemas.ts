import { z } from 'zod'

/**
 * AbEvent Schemas
 *
 * Zod schemas for runtime validation of abnormality event data.
 * Types are derived from schemas via z.infer — schemas are the single source of truth.
 */

// =============================================================================
// Spec List (aggregated, for list page)
// =============================================================================

export const AbEventSpecListEntrySchema = z.object({
  relatedEgoGifts: z.array(z.string()),
  relatedThemePacks: z.array(z.string()),
  hasImage: z.boolean(),
  illustId: z.string().optional(),
})

export const AbEventSpecListSchema = z.record(z.string(), AbEventSpecListEntrySchema)

export const AbEventNameListSchema = z.record(z.string(), z.string())

// =============================================================================
// Mechanics (individual abEvent/{id}.json)
// =============================================================================

export const AbEventRewardSchema = z.object({
  type: z.string(),
  id: z.number().nullable(),
  num: z.number(),
  prob: z.number(),
})

export const AbEventEffectSchema = z.object({
  effect: z.string(),
  target: z.string().optional(),
  condition: z.string().optional(),
  descId: z.string().optional(),
  reward: AbEventRewardSchema.optional(),
  nextBattleId: z.number().optional(),
})

const AbEventProbabilityResultSchema = z.object({
  probability: z.number(),
  effects: z.array(AbEventEffectSchema),
  nextEventId: z.number().optional(),
})

const AbEventConditionalResultSchema = z.object({
  condition: z.string().optional(),
  probability: z.number().optional(),
  effects: z.array(AbEventEffectSchema),
  nextEventId: z.number().optional(),
})

export const AbEventChoiceSchema = z.object({
  index: z.number(),
  cantSelectInThisCase: z.string().optional(),
  nextEventId: z.number().optional(),
  directEffects: z.array(AbEventEffectSchema).optional(),
  probabilityResults: z.array(AbEventProbabilityResultSchema).optional(),
  conditionalResults: z.array(AbEventConditionalResultSchema).optional(),
})

export const AbEventJudgementSchema = z.object({
  successThreshold: z.number(),
  bestThreshold: z.number(),
  affinities: z.array(z.string()),
})

export const AbEventResultSchema = z.object({
  outcome: z.string(),
  effects: z.array(AbEventEffectSchema),
  nextEventId: z.number().optional(),
  subResults: z.array(AbEventConditionalResultSchema).optional(),
})

export const AbEventSelectionEventSchema = z.object({
  canSkip: z.boolean(),
  eventType: z.string().optional(),
  participantInfo: z
    .object({
      min: z.number(),
      max: z.number(),
    })
    .optional(),
  adderInfo: z.array(z.object({ correctionCase: z.string(), adder: z.number() })).optional(),
  judgement: AbEventJudgementSchema.optional(),
  results: z.array(AbEventResultSchema).optional(),
})

// Sub-event schema (recursive — sub-events have the same structure as parent but no further subEvents)
const AbEventSubEventSchema = z.object({
  canSkip: z.boolean().optional(),
  eventType: z.string().optional(),
  choices: z.array(AbEventChoiceSchema).optional(),
  selectionEvents: z.record(z.string(), AbEventSelectionEventSchema).optional(),
})

export const AbEventDataSchema = z.object({
  canSkip: z.boolean(),
  eventType: z.string(),
  isHideHint: z.boolean().optional(),
  choices: z.array(AbEventChoiceSchema).optional(),
  selectionEvents: z.record(z.string(), AbEventSelectionEventSchema).optional(),
  subEvents: z.record(z.string(), AbEventSubEventSchema).optional(),
})

// =============================================================================
// i18n (individual i18n/{lang}/abEvent/{id}.json)
// =============================================================================

export const AbEventOptionI18nSchema = z.object({
  message: z.string(),
  messageDesc: z.string().optional(),
  result: z.array(z.string()).optional(),
})

export const AbEventSelectionTextI18nSchema = z.object({
  title: z.string().optional(),
  behaveDesc: z.string().optional(),
  successDesc: z.union([z.string(), z.array(z.string())]).optional(),
  failureDesc: z.union([z.string(), z.array(z.string())]).optional(),
})

export const AbEventI18nSchema = z.object({
  name: z.string().optional(),
  desc: z.string().optional(),
  subDesc: z.string().optional(),
  title: z.string().optional(),
  prevDesc: z.string().optional(),
  eventDesc: z.string().optional(),
  behaveDesc: z.string().optional(),
  successDesc: z.union([z.string(), z.array(z.string())]).optional(),
  failureDesc: z.union([z.string(), z.array(z.string())]).optional(),
  options: z.array(AbEventOptionI18nSchema).optional(),
  selectionTexts: z.record(z.string(), AbEventSelectionTextI18nSchema).optional(),
  choiceEffects: z.record(z.string(), z.array(z.string())).optional(),
  subEventTexts: z
    .record(
      z.string(),
      z.object({
        name: z.string().optional(),
        desc: z.string().optional(),
        options: z.array(AbEventOptionI18nSchema).optional(),
        selectionTexts: z.record(z.string(), AbEventSelectionTextI18nSchema).optional(),
      }),
    )
    .optional(),
})

// =============================================================================
// Shared resources (i18n/{lang}/abEvent/_shared.json)
// =============================================================================

export const AbEventSharedSchema = z.object({
  effects: z.record(z.string(), z.string()),
  targets: z.record(z.string(), z.string()),
  keywords: z.record(z.string(), z.string()),
  affinities: z.record(z.string(), z.string()).optional(),
  unitKeywords: z.record(z.string(), z.string()).optional(),
  sinnerNames: z.record(z.string(), z.string()).optional(),
  identityNames: z.record(z.string(), z.string()).optional(),
  resultLogs: z.record(z.string(), z.string()).optional(),
})

// =============================================================================
// Derived Types
// =============================================================================

export type AbEventSpecListEntry = z.infer<typeof AbEventSpecListEntrySchema>
export type AbEventSpecList = z.infer<typeof AbEventSpecListSchema>
export type AbEventData = z.infer<typeof AbEventDataSchema>
export type AbEventEffect = z.infer<typeof AbEventEffectSchema>
export type AbEventChoice = z.infer<typeof AbEventChoiceSchema>
export type AbEventJudgement = z.infer<typeof AbEventJudgementSchema>
export type AbEventResult = z.infer<typeof AbEventResultSchema>
export type AbEventSelectionEvent = z.infer<typeof AbEventSelectionEventSchema>
export type AbEventI18n = z.infer<typeof AbEventI18nSchema>
export type AbEventShared = z.infer<typeof AbEventSharedSchema>
