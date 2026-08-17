import { z } from 'zod'
import { UserRoleSchema } from '@/shared/auth'

/**
 * User data for moderation dashboard
 */
export const UserForModSchema = z.object({
  usernameEpithet: z.string(),
  usernameSuffix: z.string(),
  role: UserRoleSchema,
  isBanned: z.boolean(),
  bannedAt: z.string().optional(),
  isTimedOut: z.boolean(),
  timeoutUntil: z.string().optional(),
})

/**
 * Every action the audit log can record, mirroring the backend
 * `ModerationAction.ActionType` constants.
 */
export const ModerationActionTypeSchema = z.enum([
  'BAN',
  'UNBAN',
  'TIMEOUT',
  'CLEAR_TIMEOUT',
  'PROMOTE',
  'DEMOTE',
  'DELETE_PLANNER',
  'DELETE_COMMENT',
  'UNPUBLISH_PLANNER',
  'HIDE_FROM_RECOMMENDED',
  'UNHIDE_FROM_RECOMMENDED',
])

/**
 * Moderation action audit log entry
 */
export const ModerationActionSchema = z.object({
  actionType: ModerationActionTypeSchema,
  targetType: z.enum(['USER', 'PLANNER', 'COMMENT']),
  targetUuid: z.string(),
  reason: z.string(),
  durationMinutes: z.number(),
  createdAt: z.string(),
  actorUsernameEpithet: z.string(),
  actorUsernameSuffix: z.string(),
})
