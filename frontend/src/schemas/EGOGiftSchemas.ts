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

// EGOGiftSpec schema - specification data from EGOGiftSpecList.json
export const EGOGiftSpecSchema = z.object({
  category: z.string(),
  keywords: z.array(z.string()),
  themePack: z.array(z.string()),
  tier: z.string(),
}).strict()

// EGOGiftData schema - detail data from egoGift/{id}.json
export const EGOGiftDataSchema = z.object({
  category: z.string(),
  tier: z.string(),
  cost: z.number(),
}).strict()

// EGOGiftI18n schema - i18n data from gift/{id}.json
export const EGOGiftI18nSchema = z.object({
  name: z.string(),
  descs: z.array(z.string()),
  obtain: z.string(),
}).strict()

// EGOGift schema - combined data for UI consumption (list page)
export const EGOGiftSchema = z.object({
  id: z.string(),
  name: z.string(),
  category: z.string(),
  keywords: z.array(z.string()),
  themePack: z.array(z.string()),
  tier: z.string(),
  enhancement: z.number(),
}).strict()

// Record types for spec and name lists
export const EGOGiftSpecListSchema = z.record(z.string(), EGOGiftSpecSchema)
export const EGOGiftNameListSchema = z.record(z.string(), z.string())
