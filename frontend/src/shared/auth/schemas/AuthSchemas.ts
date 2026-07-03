import { z } from 'zod';

/**
 * User schema for authentication
 * Includes restriction status (ban/timeout) for UI-level enforcement
 * Tokens are stored in HttpOnly cookies (not in response)
 */
export const UserSchema = z.object({
  email: z.string().email({ message: 'Invalid email format' }),
  usernameEpithet: z.string(),
  usernameSuffix: z.string(),
  role: z.enum(['NORMAL', 'MODERATOR', 'ADMIN']),

  // Restriction status (optional - only present if restricted)
  isBanned: z.boolean().optional(),
  bannedAt: z.string().datetime().optional(),
  banReason: z.string().optional(),
  isTimedOut: z.boolean().optional(),
  timeoutUntil: z.string().datetime().optional(),
  timeoutReason: z.string().optional(),
}).strict();

export type User = z.infer<typeof UserSchema>;
