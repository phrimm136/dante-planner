import { z } from 'zod';

/**
 * Sanity Condition Schemas
 *
 * Zod schemas for runtime validation of sanity condition i18n data.
 * Maps function names to increment/decrement description templates.
 *
 * Template format uses {0}, {1}, {2} placeholders for argument substitution.
 */

// Single sanity condition entry with inc/dec templates
export const SanityConditionEntrySchema = z.object({
  /** Template for increment condition (e.g., "Increase by {0} after...") */
  inc: z.string(),
  /** Template for decrement condition (e.g., "Decrease by {0} after...") */
  dec: z.string(),
}).strict();

// Full sanity condition i18n map - function name to entry
export const SanityConditionI18nSchema = z.record(z.string(), SanityConditionEntrySchema);

// Type exports
export type SanityConditionEntry = z.infer<typeof SanityConditionEntrySchema>;
export type SanityConditionI18n = z.infer<typeof SanityConditionI18nSchema>;
