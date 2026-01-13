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

// Recipe schemas for EGO Gift fusion/combination
// Standard recipe: multiple recipe options, each with fixed ingredient IDs
const StandardRecipeSchema = z.object({
  materials: z.array(z.array(z.number())),
}).strict()

// Mixed recipe (Lunar Memory only): pick N from pool A + M from pool B
const MixedRecipeSchema = z.object({
  type: z.literal('mixed'),
  a: z.object({ ids: z.array(z.number()), count: z.number() }).strict(),
  b: z.object({ ids: z.array(z.number()), count: z.number() }).strict(),
}).strict()

// Union type - discriminated by presence of 'type' field
const EGOGiftRecipeSchema = z.union([MixedRecipeSchema, StandardRecipeSchema])

// EGOGiftSpec schema - specification data from egoGiftSpecList.json
export const EGOGiftSpecSchema = z.object({
  tag: tagArraySchema,
  keyword: z.string().nullable(),
  attributeType: z.string(),
  themePack: z.array(z.string()),
  maxEnhancement: z.union([z.literal(0), z.literal(1), z.literal(2)]),
  hardOnly: z.boolean().optional(),
  extremeOnly: z.boolean().optional(),
  recipe: EGOGiftRecipeSchema.optional(),
}).strict()

// EGOGiftData schema - detail data from egoGift/{id}.json
export const EGOGiftDataSchema = z.object({
  tag: tagArraySchema,
  keyword: z.string().nullable(),
  attributeType: z.string(),
  price: z.number(),
  themePack: z.array(z.string()),
  hardOnly: z.boolean().optional(),
  extremeOnly: z.boolean().optional(),
  maxEnhancement: z.union([z.literal(0), z.literal(1), z.literal(2)]),
  recipe: EGOGiftRecipeSchema.optional(),
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
  themePack: z.array(z.string()),
  maxEnhancement: z.union([z.literal(0), z.literal(1), z.literal(2)]),
  hardOnly: z.boolean().optional(),
  extremeOnly: z.boolean().optional(),
  recipe: EGOGiftRecipeSchema.optional(),
}).strict()

// Record types for spec and name lists
export const EGOGiftSpecListSchema = z.record(z.string(), EGOGiftSpecSchema)
export const EGOGiftNameListSchema = z.record(z.string(), z.string())
