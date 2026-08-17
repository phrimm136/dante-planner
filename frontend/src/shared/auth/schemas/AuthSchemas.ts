import { z } from 'zod'

/** Account roles, ascending in privilege. */
export const USER_ROLES = ['NORMAL', 'MODERATOR', 'ADMIN'] as const

export const UserRoleSchema = z.enum(USER_ROLES)

export type UserRole = z.infer<typeof UserRoleSchema>

/** Roles that grant moderation surfaces (moderator dashboard, delete-any). */
export function isStaff(role: UserRole | null | undefined): boolean {
  return role === 'MODERATOR' || role === 'ADMIN'
}

/**
 * User schema for authentication
 * Includes restriction status (ban/timeout) for UI-level enforcement
 * Tokens are stored in HttpOnly cookies (not in response)
 */
export const UserSchema = z
  .object({
    email: z.string().email({ message: 'Invalid email format' }),
    usernameEpithet: z.string(),
    usernameSuffix: z.string(),
    role: UserRoleSchema,

    // Restriction status (optional - only present if restricted)
    isBanned: z.boolean().optional(),
    bannedAt: z.string().datetime().optional(),
    banReason: z.string().optional(),
    isTimedOut: z.boolean().optional(),
    timeoutUntil: z.string().datetime().optional(),
    timeoutReason: z.string().optional(),
  })
  .strict()

export type User = z.infer<typeof UserSchema>
