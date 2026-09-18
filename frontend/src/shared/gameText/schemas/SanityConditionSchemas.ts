import { z } from 'zod'

/**
 * Sanity Condition Schemas
 *
 * Zod schemas for runtime validation of sanity condition i18n data.
 * Maps function names to increment/decrement description templates.
 *
 * Template format uses {0}, {1}, {2} placeholders for argument substitution.
 */

export const SanityConditionEntrySchema = z
  .object({
    inc: z.string(),
    dec: z.string(),
  })
  .strict()

export const SanityConditionI18nSchema = z.record(z.string(), SanityConditionEntrySchema)

export type SanityConditionI18n = z.infer<typeof SanityConditionI18nSchema>
