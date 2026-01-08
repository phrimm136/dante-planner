/**
 * User Settings Types
 *
 * TypeScript types for user settings data structures.
 * Used for username customization feature.
 */

/**
 * Association option for username keyword selection.
 * Maps to backend AssociationDto.
 */
export interface Association {
  /** Internal keyword in UPPER_SNAKE_CASE (e.g., "W_CORP") */
  keyword: string
  /** Human-readable display name (e.g., "WCorp") */
  displayName: string
}

/**
 * Response containing all available associations.
 * Maps to backend AssociationListResponse.
 */
export interface AssociationListResponse {
  associations: Association[]
}

/**
 * Request to update username keyword.
 * Maps to backend UpdateUsernameKeywordRequest.
 */
export interface UpdateUsernameKeywordRequest {
  keyword: string
}
