import { z } from 'zod'

export const USER_ROLES = ['NORMAL', 'MODERATOR', 'ADMIN'] as const

export const UserRoleSchema = z.enum(USER_ROLES)

export type UserRole = z.infer<typeof UserRoleSchema>

export function isStaff(role: UserRole | null | undefined): boolean {
  return role === 'MODERATOR' || role === 'ADMIN'
}

export const UserSchema = z
  .object({
    email: z.string().email({ message: 'Invalid email format' }),
    usernameEpithet: z.string(),
    usernameSuffix: z.string(),
    role: UserRoleSchema,

    isBanned: z.boolean().optional(),
    bannedAt: z.string().datetime().optional(),
    banReason: z.string().optional(),
    isTimedOut: z.boolean().optional(),
    timeoutUntil: z.string().datetime().optional(),
    timeoutReason: z.string().optional(),
  })
  .strict()

export type User = z.infer<typeof UserSchema>
