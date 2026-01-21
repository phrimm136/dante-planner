import { z } from 'zod'

/**
 * User Settings Schemas
 *
 * Zod schemas for runtime validation of user settings data structures.
 * These schemas mirror the TypeScript interfaces in types/UserSettingsTypes.ts.
 */

// Epithet list response schema
export const EpithetListResponseSchema = z.object({
  epithets: z.array(z.string()),
})

// Update epithet request schema (for validation)
export const UpdateUsernameEpithetRequestSchema = z.object({
  epithet: z.string().min(1, 'Epithet is required'),
})

// User deletion response schema
export const UserDeletionResponseSchema = z.object({
  message: z.string(),
  deletedAt: z.string(),
  permanentDeleteAt: z.string(),
  gracePeriodDays: z.number(),
}).strict()

// User settings response schema (sync and notification preferences)
export const UserSettingsResponseSchema = z.object({
  syncEnabled: z.boolean().nullable(),
  notifyComments: z.boolean(),
  notifyRecommendations: z.boolean(),
  notifyNewPublications: z.boolean(),
}).strict()

// Update user settings request schema (partial update, all optional)
export const UpdateUserSettingsRequestSchema = z.object({
  syncEnabled: z.boolean().optional(),
  notifyComments: z.boolean().optional(),
  notifyRecommendations: z.boolean().optional(),
  notifyNewPublications: z.boolean().optional(),
}).strict()

// Type exports from schemas
export type EpithetListResponse = z.infer<typeof EpithetListResponseSchema>
export type UpdateUsernameEpithetRequest = z.infer<typeof UpdateUsernameEpithetRequestSchema>
export type UserDeletionResponse = z.infer<typeof UserDeletionResponseSchema>
export type UserSettingsResponse = z.infer<typeof UserSettingsResponseSchema>
export type UpdateUserSettingsRequest = z.infer<typeof UpdateUserSettingsRequestSchema>
