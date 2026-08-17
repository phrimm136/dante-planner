import { z } from 'zod'

/**
 * User Settings Schemas
 *
 * Zod schemas for runtime validation of the sync and notification
 * preferences. Types are derived via z.infer — schemas are the single
 * source of truth.
 */

/**
 * User settings response for sync and notification preferences.
 * Maps to backend UserSettingsResponse.
 */
export const UserSettingsResponseSchema = z
  .object({
    /** Whether sync is enabled */
    syncEnabled: z.boolean(),
    /** Whether the user has answered the sync prompt; false triggers the first-login dialog */
    syncChoiceMade: z.boolean(),
    /** Notify when someone comments on your planner */
    notifyComments: z.boolean(),
    /** Notify when your planner reaches recommended status */
    notifyRecommendations: z.boolean(),
    /** Notify when someone publishes a new planner */
    notifyNewPublications: z.boolean(),
  })
  .strict()

/**
 * Request to update user settings (partial update, all optional).
 * Maps to backend UpdateUserSettingsRequest.
 */
export const UpdateUserSettingsRequestSchema = z
  .object({
    syncEnabled: z.boolean().optional(),
    notifyComments: z.boolean().optional(),
    notifyRecommendations: z.boolean().optional(),
    notifyNewPublications: z.boolean().optional(),
  })
  .strict()

export type UserSettingsResponse = z.infer<typeof UserSettingsResponseSchema>
export type UpdateUserSettingsRequest = z.infer<typeof UpdateUserSettingsRequestSchema>
