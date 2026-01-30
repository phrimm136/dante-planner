import { z } from 'zod'

/**
 * User data for moderation dashboard
 */
export const UserForModSchema = z.object({
  usernameEpithet: z.string(),
  usernameSuffix: z.string(),
  role: z.enum(['NORMAL', 'MODERATOR', 'ADMIN']),
  isBanned: z.boolean(),
  bannedAt: z.string(),
  isTimedOut: z.boolean(),
  timeoutUntil: z.string(),
})

/**
 * Moderation action audit log entry
 */
export const ModerationActionSchema = z.object({
  actionType: z.enum(['BAN', 'UNBAN', 'TIMEOUT', 'CLEAR_TIMEOUT', 'PROMOTE', 'DEMOTE', 'DELETE_PLANNER', 'DELETE_COMMENT']),
  targetType: z.enum(['USER', 'PLANNER', 'COMMENT']),
  targetUuid: z.string(),
  reason: z.string(),
  durationMinutes: z.number(),
  createdAt: z.string(),
  actorUsernameEpithet: z.string(),
  actorUsernameSuffix: z.string(),
})
