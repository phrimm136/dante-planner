import { z } from 'zod'

/**
 * User Settings Schemas
 *
 * Zod schemas for runtime validation of user settings data structures.
 * Used for username customization, sync, and notification preferences.
 * Types are derived via z.infer — schemas are the single source of truth.
 */

/**
 * Response containing all available epithets.
 * Maps to backend EpithetListResponse.
 */
export const EpithetListResponseSchema = z.object({
  /** List of epithet keywords (e.g., "NAIVE", "BRILLIANT") */
  epithets: z.array(z.string()),
})

/**
 * Request to update username epithet.
 * Maps to backend UpdateUsernameEpithetRequest.
 */
export const UpdateUsernameEpithetRequestSchema = z.object({
  epithet: z.string().min(1, 'Epithet is required'),
})

/**
 * Response from account deletion request.
 * Maps to backend UserDeletionResponse.
 */
export const UserDeletionResponseSchema = z.object({
  /** Success message from server */
  message: z.string(),
  /** ISO 8601 timestamp when account was marked as deleted */
  deletedAt: z.string(),
  /** ISO 8601 timestamp when account will be permanently deleted */
  permanentDeleteAt: z.string(),
  /** Number of days in grace period before permanent deletion */
  gracePeriodDays: z.number(),
}).strict()

/**
 * User settings response for sync and notification preferences.
 * Maps to backend UserSettingsResponse.
 */
export const UserSettingsResponseSchema = z.object({
  /** Whether sync is enabled (null = not chosen yet, triggers first-login dialog) */
  syncEnabled: z.boolean().nullable(),
  /** Notify when someone comments on your planner */
  notifyComments: z.boolean(),
  /** Notify when your planner reaches recommended status */
  notifyRecommendations: z.boolean(),
  /** Notify when someone publishes a new planner */
  notifyNewPublications: z.boolean(),
}).strict()

/**
 * Request to update user settings (partial update, all optional).
 * Maps to backend UpdateUserSettingsRequest.
 */
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
