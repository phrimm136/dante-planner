import { z } from 'zod';

/**
 * User schema for authentication
 * Only email and provider ID are collected (minimal privacy impact)
 * Tokens are stored in HttpOnly cookies (not in response)
 */
export const UserSchema = z.object({
  id: z.string().uuid(),
  email: z.string().email({ message: 'Invalid email format' }),
  provider: z.string(),
  usernameKeyword: z.string(),
  usernameSuffix: z.string(),
}).strict();

export type User = z.infer<typeof UserSchema>;
