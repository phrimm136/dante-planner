import { z } from 'zod'

/**
 * User Settings Schemas
 *
 * Zod schemas for runtime validation of user settings data structures.
 * Used for username customization and account deletion.
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
export const UserDeletionResponseSchema = z
  .object({
    /** Success message from server */
    message: z.string(),
    /** ISO 8601 timestamp when account was marked as deleted */
    deletedAt: z.string(),
    /** ISO 8601 timestamp when account will be permanently deleted; absent when a prior deletion left none scheduled */
    permanentDeleteAt: z.string().nullish(),
    /** Number of days in grace period before permanent deletion */
    gracePeriodDays: z.number(),
  })
  .strict()

// Type exports from schemas
export type EpithetListResponse = z.infer<typeof EpithetListResponseSchema>
export type UpdateUsernameEpithetRequest = z.infer<typeof UpdateUsernameEpithetRequestSchema>
export type UserDeletionResponse = z.infer<typeof UserDeletionResponseSchema>
