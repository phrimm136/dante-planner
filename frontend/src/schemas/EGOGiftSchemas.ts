import { z } from 'zod'

/**
 * EGO Gift Schemas
 *
 * Zod schemas for runtime validation of EGO Gift data structures.
 * These schemas mirror the TypeScript interfaces in types/EGOGiftTypes.ts
 * and provide strict runtime validation with comprehensive error collection.
 *
 * MAINTENANCE: When TypeScript interfaces change, regenerate schemas using
 * the shared source generation tooling to maintain synchronization.
 */

// Tag array validator - ensures at least one "TIER_*" tag exists
const tagArraySchema = z.array(z.string()).refine(
  (tags) => tags.some((tag) => tag.startsWith('TIER_')),
  {
    message: 'tag array must contain at least one "TIER_*" string',
  }
)

// EGOGiftSpec schema - specification data from egoGiftSpecList.json
export const EGOGiftSpecSchema = z.object({
  tag: tagArraySchema,
  keyword: z.string().nullable(),
  attributeType: z.string(),
}).strict()

// EGOGiftData schema - detail data from egoGift/{id}.json
export const EGOGiftDataSchema = z.object({
  tag: tagArraySchema,
  keyword: z.string().nullable(),
  attributeType: z.string(),
  price: z.number(),
}).strict()

// EGOGiftI18n schema - i18n data from egoGift/{id}.json (i18n folder)
export const EGOGiftI18nSchema = z.object({
  name: z.string(),
  descs: z.array(z.string()),
  obtain: z.string(),
}).strict()

// EGOGiftListItem schema - merged data for list view
export const EGOGiftListItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  tag: tagArraySchema,
  keyword: z.string().nullable(),
  attributeType: z.string(),
}).strict()

// Record types for spec and name lists
export const EGOGiftSpecListSchema = z.record(z.string(), EGOGiftSpecSchema)
export const EGOGiftNameListSchema = z.record(z.string(), z.string())
