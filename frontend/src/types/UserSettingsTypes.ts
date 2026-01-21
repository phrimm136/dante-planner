/**
 * User Settings Types
 *
 * TypeScript types for user settings data structures.
 * Used for username customization feature.
 */

/**
 * Response containing all available epithets.
 * Maps to backend EpithetListResponse.
 */
export interface EpithetListResponse {
  /** List of epithet keywords (e.g., "NAIVE", "BRILLIANT") */
  epithets: string[]
}

/**
 * Request to update username epithet.
 * Maps to backend UpdateUsernameEpithetRequest.
 */
export interface UpdateUsernameEpithetRequest {
  epithet: string
}

/**
 * Response from account deletion request.
 * Maps to backend UserDeletionResponse.
 */
export interface UserDeletionResponse {
  /** Success message from server */
  message: string
  /** ISO 8601 timestamp when account was marked as deleted */
  deletedAt: string
  /** ISO 8601 timestamp when account will be permanently deleted */
  permanentDeleteAt: string
  /** Number of days in grace period before permanent deletion */
  gracePeriodDays: number
}

/**
 * User settings response for sync and notification preferences.
 * Maps to backend UserSettingsResponse.
 */
export interface UserSettingsResponse {
  /** Whether sync is enabled (null = not chosen yet, triggers first-login dialog) */
  syncEnabled: boolean | null
  /** Notify when someone comments on your planner */
  notifyComments: boolean
  /** Notify when your planner reaches recommended status */
  notifyRecommendations: boolean
  /** Notify when someone publishes a new planner */
  notifyNewPublications: boolean
}

/**
 * Request to update user settings (partial update, all optional).
 * Maps to backend UpdateUserSettingsRequest.
 */
export interface UpdateUserSettingsRequest {
  syncEnabled?: boolean
  notifyComments?: boolean
  notifyRecommendations?: boolean
  notifyNewPublications?: boolean
}
